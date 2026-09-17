package com.kiftd.service;

import com.kiftd.common.BizException;
import com.kiftd.config.KiftdProperties;
import com.kiftd.dto.FileDtos;
import com.kiftd.entity.FileNode;
import com.kiftd.repository.FileNodeRepository;
import com.kiftd.util.StorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PreviewService {

    private final FileNodeRepository fileNodeRepository;
    private final FileService fileService;
    private final FolderService folderService;
    private final StorageService storageService;
    private final KiftdProperties props;
    private final Map<String, String> transcodeStatus = new ConcurrentHashMap<>();

    public PreviewService(FileNodeRepository fileNodeRepository, FileService fileService,
                          FolderService folderService, StorageService storageService, KiftdProperties props) {
        this.fileNodeRepository = fileNodeRepository;
        this.fileService = fileService;
        this.folderService = folderService;
        this.storageService = storageService;
        this.props = props;
    }

    public FileDtos.PictureViewList pictures(String fileId) {
        FileNode current = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(current.getFileParentFolder()));
        List<FileNode> files = fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(current.getFileParentFolder());
        List<FileDtos.PictureInfo> list = new ArrayList<>();
        int index = 0;
        int i = 0;
        for (FileNode f : files) {
            if (isImage(f.getFileName())) {
                if (f.getFileId().equals(fileId)) {
                    index = i;
                }
                list.add(new FileDtos.PictureInfo(f.getFileId(), f.getFileName(),
                        "/api/preview/resource/" + f.getFileId()));
                i++;
            }
        }
        return new FileDtos.PictureViewList(list, index);
    }

    public List<FileDtos.AudioInfo> audios(String folderId) {
        folderService.checkAccess(folderService.requireFolder(folderId));
        List<FileDtos.AudioInfo> list = new ArrayList<>();
        for (FileNode f : fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(folderId)) {
            if (isAudio(f.getFileName())) {
                list.add(new FileDtos.AudioInfo(f.getFileId(), f.getFileName(),
                        "/api/preview/resource/" + f.getFileId(), "未知艺术家", ""));
            }
        }
        return list;
    }

    public FileDtos.VideoInfo video(String fileId) {
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        boolean need = !node.getFileName().toLowerCase(Locale.ROOT).endsWith(".mp4");
        return new FileDtos.VideoInfo(fileId, node.getFileName(), need);
    }

    public String transcodeStatus(String fileId) {
        FileNode node = fileService.requireFile(fileId);
        if (node.getFileName().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            return "FIN";
        }
        String status = transcodeStatus.get(fileId);
        if (status == null) {
            startTranscode(fileId, node);
            return "0%";
        }
        return status;
    }

    private void startTranscode(String fileId, FileNode node) {
        transcodeStatus.put(fileId, "10%");
        Thread.ofVirtual().start(() -> {
            try {
                Path src = storageService.resolveBlock(node.getFilePath());
                Path out = storageService.tempFile(".mp4");
                ProcessBuilder pb = new ProcessBuilder(
                        props.ffmpeg().path(), "-y", "-i", src.toString(),
                        "-c:v", "libx264", "-c:a", "aac", out.toString());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                transcodeStatus.put(fileId, "50%");
                int code = p.waitFor();
                if (code == 0 && Files.exists(out)) {
                    // replace block with mp4 for streaming convenience
                    try (InputStream in = Files.newInputStream(out)) {
                        String newPath = storageService.saveNewBlock(in);
                        storageService.deleteBlock(node.getFilePath());
                        node.setFilePath(newPath);
                        if (!node.getFileName().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                            node.setFileName(stripExt(node.getFileName()) + ".mp4");
                        }
                        fileNodeRepository.save(node);
                    }
                    Files.deleteIfExists(out);
                    transcodeStatus.put(fileId, "FIN");
                } else {
                    transcodeStatus.put(fileId, "ERROR");
                }
            } catch (Exception e) {
                transcodeStatus.put(fileId, "ERROR");
            }
        });
    }

    public void writeTxtAsPdf(String fileId, OutputStream out) throws IOException {
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        Path path = storageService.resolveBlock(node.getFilePath());
        String text = Files.readString(path, Charset.forName("UTF-8"));
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(50, 780);
                int line = 0;
                for (String raw : text.split("\\R")) {
                    String s = raw.length() > 90 ? raw.substring(0, 90) : raw;
                    cs.showText(s.replace("\t", "  "));
                    cs.newLineAtOffset(0, -14);
                    line++;
                    if (line > 50) break;
                }
                cs.endText();
            }
            applyPdfTitle(doc, node.getFileName());
            doc.save(out);
        }
    }

    public void writePdfDirect(String fileId, OutputStream out) throws IOException {
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        Path path = storageService.resolveBlock(node.getFilePath());
        // 用正确文件名覆盖 PDF 内嵌 Title，避免浏览器 PDF 工具栏显示乱码
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            applyPdfTitle(doc, node.getFileName());
            doc.save(out);
        } catch (Exception e) {
            Files.copy(path, out);
        }
    }

    public void writeOfficeAsPdf(String fileId, OutputStream out) throws IOException {
        // Simplified: for docx try poi converter; otherwise wrap as text notice pdf
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        Path path = storageService.resolveBlock(node.getFilePath());
        String lower = node.getFileName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            try (InputStream in = Files.newInputStream(path);
                 org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(in);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                fr.opensagres.poi.xwpf.converter.pdf.PdfConverter.getInstance()
                        .convert(document, bos, null);
                writePdfWithTitle(bos.toByteArray(), node.getFileName(), out);
                return;
            } catch (Exception e) {
                throw new BizException("Word 转 PDF 失败: " + e.getMessage());
            }
        }
        // ppt/pptx fallback: simple notice
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                cs.newLineAtOffset(50, 750);
                cs.showText("Preview conversion for this Office type is limited.");
                cs.newLineAtOffset(0, -20);
                cs.showText("File: " + asciiFallback(node.getFileName()));
                cs.endText();
            }
            applyPdfTitle(doc, node.getFileName());
            doc.save(out);
        }
    }

    private static void writePdfWithTitle(byte[] pdfBytes, String fileName, OutputStream out) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            applyPdfTitle(doc, fileName);
            doc.save(out);
        } catch (Exception e) {
            out.write(pdfBytes);
        }
    }

    private static void applyPdfTitle(PDDocument doc, String fileName) {
        if (doc == null || fileName == null || fileName.isBlank()) return;
        PDDocumentInformation info = doc.getDocumentInformation();
        if (info == null) {
            info = new PDDocumentInformation();
            doc.setDocumentInformation(info);
        }
        info.setTitle(fileName.trim());
        try {
            doc.getDocumentCatalog().setLanguage("zh-CN");
        } catch (Exception ignored) {
            /* ignore */
        }
    }

    private static String asciiFallback(String name) {
        String ascii = name.replaceAll("[^\\x20-\\x7E]", "_");
        return ascii.isBlank() ? "file" : ascii;
    }

    public static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    public static boolean isAudio(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".m4a");
    }

    public static boolean isVideo(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov") || n.endsWith(".webm");
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
