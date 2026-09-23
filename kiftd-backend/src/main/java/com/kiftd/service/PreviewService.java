package com.kiftd.service;

import com.kiftd.common.BizException;
import com.kiftd.config.KiftdProperties;
import com.kiftd.dto.FileDtos;
import com.kiftd.entity.FileNode;
import com.kiftd.repository.FileNodeRepository;
import com.kiftd.util.NaturalOrder;
import com.kiftd.util.StorageService;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PreviewService {

    private final FileNodeRepository fileNodeRepository;
    private final FileService fileService;
    private final FolderService folderService;
    private final StorageService storageService;
    private final KiftdProperties props;
    private final Map<String, String> transcodeStatus = new ConcurrentHashMap<>();
    private final Map<String, byte[]> pptSlideCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> thumbLocks = new ConcurrentHashMap<>();
    private static final int THUMB_MAX_EDGE = 480;

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
        List<FileNode> files = listSameFolderNatural(current.getFileParentFolder()).stream()
                .filter(f -> isImage(f.getFileName()))
                .toList();
        List<FileDtos.PictureInfo> list = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < files.size(); i++) {
            FileNode f = files.get(i);
            if (f.getFileId().equals(fileId)) {
                index = i;
            }
            list.add(new FileDtos.PictureInfo(f.getFileId(), f.getFileName(),
                    props.apiPath("/preview/resource/" + f.getFileId()),
                    nullToEmpty(f.getFileCreationDate()),
                    nullToEmpty(f.getFileSize())));
        }
        return new FileDtos.PictureViewList(list, index);
    }

    public List<FileDtos.AudioInfo> audios(String folderId) {
        folderService.checkAccess(folderService.requireFolder(folderId));
        List<FileDtos.AudioInfo> list = new ArrayList<>();
        for (FileNode f : listSameFolderNatural(folderId)) {
            if (isAudio(f.getFileName())) {
                list.add(new FileDtos.AudioInfo(f.getFileId(), f.getFileName(),
                        props.apiPath("/preview/resource/" + f.getFileId()), "未知艺术家", "",
                        nullToEmpty(f.getFileCreationDate()),
                        nullToEmpty(f.getFileSize())));
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

    public FileDtos.VideoViewList videos(String fileId) {
        FileNode current = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(current.getFileParentFolder()));
        List<FileNode> files = listSameFolderNatural(current.getFileParentFolder()).stream()
                .filter(f -> isVideo(f.getFileName()))
                .toList();
        List<FileDtos.VideoItem> list = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < files.size(); i++) {
            FileNode f = files.get(i);
            if (f.getFileId().equals(fileId)) {
                index = i;
            }
            list.add(new FileDtos.VideoItem(f.getFileId(), f.getFileName(),
                    nullToEmpty(f.getFileCreationDate()),
                    nullToEmpty(f.getFileSize())));
        }
        return new FileDtos.VideoViewList(list, index);
    }

    public FileDtos.SiblingViewList siblings(String fileId) {
        FileNode current = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(current.getFileParentFolder()));
        String category = previewCategory(current.getFileName());
        List<FileDtos.SiblingItem> list = new ArrayList<>();
        int index = 0;
        if (category.isEmpty()) {
            list.add(new FileDtos.SiblingItem(current.getFileId(), current.getFileName(),
                    nullToEmpty(current.getFileCreationDate()),
                    nullToEmpty(current.getFileSize())));
            return new FileDtos.SiblingViewList(list, 0, category);
        }
        List<FileNode> files = listSameFolderNatural(current.getFileParentFolder()).stream()
                .filter(f -> category.equals(previewCategory(f.getFileName())))
                .toList();
        for (int i = 0; i < files.size(); i++) {
            FileNode f = files.get(i);
            if (f.getFileId().equals(fileId)) {
                index = i;
            }
            list.add(new FileDtos.SiblingItem(f.getFileId(), f.getFileName(),
                    nullToEmpty(f.getFileCreationDate()),
                    nullToEmpty(f.getFileSize())));
        }
        return new FileDtos.SiblingViewList(list, index, category);
    }

    /** 同目录文件：默认按创建时间倒序，同时间再按文件名自然序（与主列表一致）。 */
    private List<FileNode> listSameFolderNatural(String folderId) {
        List<FileNode> files = new ArrayList<>(
                fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(folderId));
        files.sort((a, b) -> {
            int cmp = nullToEmpty(b.getFileCreationDate()).compareTo(nullToEmpty(a.getFileCreationDate()));
            if (cmp != 0) {
                return cmp;
            }
            return NaturalOrder.compare(a.getFileName(), b.getFileName());
        });
        return files;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public FileDtos.ExcelPreview excelPreview(String fileId) {
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        if (!isExcel(node.getFileName())) {
            throw new BizException("不是 Excel 文件");
        }
        Path path = storageService.resolveBlock(node.getFilePath());
        try {
            if (Files.size(path) > 20L * 1024 * 1024) {
                throw new BizException("表格过大（超过 20MB），请下载后查看");
            }
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }
        try (InputStream in = Files.newInputStream(path);
             org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(in)) {
            org.apache.poi.ss.usermodel.DataFormatter formatter =
                    new org.apache.poi.ss.usermodel.DataFormatter(Locale.CHINA);
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator =
                    wb.getCreationHelper().createFormulaEvaluator();
            List<FileDtos.ExcelSheet> sheets = new ArrayList<>();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                if (wb.isSheetHidden(s) || wb.isSheetVeryHidden(s)) {
                    continue;
                }
                sheets.add(readExcelSheet(sheet, formatter, evaluator));
            }
            if (sheets.isEmpty() && wb.getNumberOfSheets() > 0) {
                sheets.add(readExcelSheet(wb.getSheetAt(0), formatter, evaluator));
            }
            return new FileDtos.ExcelPreview(node.getFileName(), sheets);
        } catch (BizException e) {
            throw e;
        } catch (org.apache.poi.EncryptedDocumentException e) {
            throw new BizException("文件已加密，无法预览");
        } catch (Exception e) {
            throw new BizException("Excel 解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static FileDtos.ExcelSheet readExcelSheet(org.apache.poi.ss.usermodel.Sheet sheet,
                                                      org.apache.poi.ss.usermodel.DataFormatter formatter,
                                                      org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        String name = sheet.getSheetName() == null || sheet.getSheetName().isBlank() ? "Sheet" : sheet.getSheetName();
        if (sheet.getPhysicalNumberOfRows() <= 0) {
            return new FileDtos.ExcelSheet(name, List.of(), false);
        }
        int lastRow = Math.min(Math.max(sheet.getLastRowNum(), 0), EXCEL_MAX_ROWS - 1);
        int maxCols = 0;
        List<List<String>> rows = new ArrayList<>();
        boolean truncated = sheet.getLastRowNum() >= EXCEL_MAX_ROWS;
        for (int r = 0; r <= lastRow; r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
            List<String> cells = new ArrayList<>();
            int lastCell = row == null ? -1 : Math.min(row.getLastCellNum() - 1, EXCEL_MAX_COLS - 1);
            if (row != null && row.getLastCellNum() > EXCEL_MAX_COLS) {
                truncated = true;
            }
            for (int c = 0; c <= lastCell; c++) {
                cells.add(formatExcelCell(row.getCell(c), formatter, evaluator));
            }
            maxCols = Math.max(maxCols, cells.size());
            rows.add(cells);
        }
        for (List<String> cells : rows) {
            while (cells.size() < maxCols) {
                cells.add("");
            }
        }
        return new FileDtos.ExcelSheet(name, rows, truncated);
    }

    public FileDtos.PptPreview pptPreview(String fileId) {
        FileNode node = requireExcelLikePpt(fileId);
        Path path = storageService.resolveBlock(node.getFilePath());
        ensurePptSize(path);
        try (InputStream in = Files.newInputStream(path);
             org.apache.poi.sl.usermodel.SlideShow<?, ?> show = org.apache.poi.sl.usermodel.SlideShowFactory.create(in)) {
            List<? extends org.apache.poi.sl.usermodel.Slide<?, ?>> slides = show.getSlides();
            int count = Math.min(slides.size(), PPT_MAX_SLIDES);
            List<FileDtos.PptSlide> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String title = slides.get(i).getTitle();
                if (title == null || title.isBlank()) {
                    title = "第 " + (i + 1) + " 页";
                } else {
                    title = title.replaceAll("\\s+", " ").trim();
                }
                list.add(new FileDtos.PptSlide(i, title));
            }
            return new FileDtos.PptPreview(node.getFileName(), list);
        } catch (BizException e) {
            throw e;
        } catch (org.apache.poi.EncryptedDocumentException e) {
            throw new BizException("文件已加密，无法预览");
        } catch (Exception e) {
            throw new BizException("PPT 解析失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    public byte[] pptSlidePng(String fileId, int index) {
        FileNode node = requireExcelLikePpt(fileId);
        Path path = storageService.resolveBlock(node.getFilePath());
        ensurePptSize(path);
        if (index < 0) {
            throw new BizException("幻灯片页码无效");
        }
        String cacheKey = fileId + "#" + index + "#" + path.toAbsolutePath() + "#" + path.toFile().lastModified()
                + "#" + com.kiftd.util.PptFontSupport.CACHE_TAG;
        byte[] cached = pptSlideCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        synchronized (pptSlideCache) {
            cached = pptSlideCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            try (InputStream in = Files.newInputStream(path);
                 org.apache.poi.sl.usermodel.SlideShow<?, ?> show = org.apache.poi.sl.usermodel.SlideShowFactory.create(in)) {
                List<? extends org.apache.poi.sl.usermodel.Slide<?, ?>> slides = show.getSlides();
                if (index >= slides.size() || index >= PPT_MAX_SLIDES) {
                    throw new BizException("幻灯片页码无效");
                }
                java.awt.Dimension pg = show.getPageSize();
                int w = Math.max(1, (int) Math.round(pg.getWidth() * PPT_SCALE));
                int h = Math.max(1, (int) Math.round(pg.getHeight() * PPT_SCALE));
                java.awt.image.BufferedImage img =
                        new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = img.createGraphics();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                    com.kiftd.util.PptFontSupport.prepareGraphics(g);
                    g.setPaint(java.awt.Color.WHITE);
                    g.fillRect(0, 0, w, h);
                    g.scale(PPT_SCALE, PPT_SCALE);
                    slides.get(index).draw(g);
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", bos);
                byte[] png = bos.toByteArray();
                if (pptSlideCache.size() > 200) {
                    pptSlideCache.clear();
                }
                pptSlideCache.put(cacheKey, png);
                return png;
            } catch (BizException e) {
                throw e;
            } catch (org.apache.poi.EncryptedDocumentException e) {
                throw new BizException("文件已加密，无法预览");
            } catch (Exception e) {
                throw new BizException("PPT 渲染失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
    }

    /**
     * 生成并缓存 JPEG 缩略图（图片 / 视频首帧 / PDF 首页 / PPT 首页），供列表与侧栏使用。
     */
    public byte[] thumbnailJpeg(String fileId) {
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        String name = node.getFileName();
        if (!supportsThumbnail(name)) {
            throw new BizException("该类型不支持缩略图");
        }
        Path src;
        try {
            src = storageService.resolveBlock(node.getFilePath());
            if (!Files.isRegularFile(src)) {
                throw new BizException("文件不存在");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("读取文件失败");
        }
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(src).toMillis();
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }
        String cacheName = fileId + "_" + mtime + "_" + THUMB_MAX_EDGE + ".jpg";
        Path cache = storageService.resolveThumb(cacheName);
        try {
            if (Files.isRegularFile(cache)) {
                return Files.readAllBytes(cache);
            }
        } catch (IOException e) {
            /* 重新生成 */
        }
        Object lock = thumbLocks.computeIfAbsent(fileId, k -> new Object());
        synchronized (lock) {
            try {
                if (Files.isRegularFile(cache)) {
                    return Files.readAllBytes(cache);
                }
                byte[] jpeg;
                if (isImage(name)) {
                    jpeg = thumbnailFromImage(src);
                } else if (isVideo(name)) {
                    jpeg = thumbnailFromVideo(src);
                } else if (isPdf(name)) {
                    jpeg = thumbnailFromPdf(src);
                } else if (isPpt(name)) {
                    jpeg = scaleToJpeg(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pptSlidePng(fileId, 0))), THUMB_MAX_EDGE);
                } else {
                    throw new BizException("该类型不支持缩略图");
                }
                Files.createDirectories(cache.getParent());
                Files.write(cache, jpeg);
                purgeOldThumbs(fileId, cacheName);
                return jpeg;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException("缩略图生成失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            } finally {
                thumbLocks.remove(fileId, lock);
            }
        }
    }

    private void purgeOldThumbs(String fileId, String keepName) {
        try (var stream = Files.list(storageService.getThumbsDir())) {
            String prefix = fileId + "_";
            stream.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".jpg") && !n.equals(keepName);
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    /* ignore */
                }
            });
        } catch (IOException ignored) {
            /* ignore */
        }
    }

    private byte[] thumbnailFromImage(Path src) throws IOException {
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(src.toFile());
        if (img == null) {
            throw new BizException("无法解析图片");
        }
        return scaleToJpeg(img, THUMB_MAX_EDGE);
    }

    private byte[] thumbnailFromPdf(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            if (doc.getNumberOfPages() < 1) {
                throw new BizException("PDF 无页面");
            }
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage img = renderer.renderImageWithDPI(0, 96, org.apache.pdfbox.rendering.ImageType.RGB);
            return scaleToJpeg(img, THUMB_MAX_EDGE);
        }
    }

    private byte[] thumbnailFromVideo(Path src) throws IOException, InterruptedException {
        Path out = storageService.tempFile(".jpg");
        try {
            if (!runFfmpegThumb(src, out, "1") && !runFfmpegThumb(src, out, "0")) {
                throw new BizException("视频缩略图生成失败（请确认已配置 ffmpeg）");
            }
            if (!Files.isRegularFile(out) || Files.size(out) < 32) {
                throw new BizException("视频缩略图为空");
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(out.toFile());
            if (img == null) {
                return Files.readAllBytes(out);
            }
            return scaleToJpeg(img, THUMB_MAX_EDGE);
        } finally {
            Files.deleteIfExists(out);
        }
    }

    private boolean runFfmpegThumb(Path src, Path out, String seekSec) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                props.ffmpeg().path(),
                "-y",
                "-ss", seekSec,
                "-i", src.toString(),
                "-frames:v", "1",
                "-vf", "scale=" + THUMB_MAX_EDGE + ":-2",
                "-q:v", "3",
                out.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return p.waitFor() == 0 && Files.isRegularFile(out);
    }

    private static byte[] scaleToJpeg(java.awt.image.BufferedImage src, int maxEdge) throws IOException {
        if (src == null) {
            throw new BizException("无法生成缩略图");
        }
        int w = Math.max(1, src.getWidth());
        int h = Math.max(1, src.getHeight());
        double scale = Math.min(1.0, (double) maxEdge / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        java.awt.image.BufferedImage out =
                new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(out, "jpg", bos)) {
            throw new BizException("JPEG 编码失败");
        }
        return bos.toByteArray();
    }

    public static boolean supportsThumbnail(String name) {
        return isImage(name) || isVideo(name) || isPdf(name) || isPpt(name);
    }

    private FileNode requireExcelLikePpt(String fileId) {
        FileNode node = fileService.requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        if (!isPpt(node.getFileName())) {
            throw new BizException("不是 PowerPoint 文件");
        }
        return node;
    }

    private static void ensurePptSize(Path path) {
        try {
            if (Files.size(path) > PPT_MAX_BYTES) {
                throw new BizException("演示文稿过大（超过 40MB），请下载后查看");
            }
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }
    }

    private static String formatExcelCell(org.apache.poi.ss.usermodel.Cell cell,
                                          org.apache.poi.ss.usermodel.DataFormatter formatter,
                                          org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                return formatter.formatCellValue(cell, evaluator);
            }
            return formatter.formatCellValue(cell);
        } catch (Exception e) {
            try {
                return formatter.formatCellValue(cell);
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    public String transcodeStatus(String fileId) {
        FileNode node = fileService.requireAccessibleFile(fileId);
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

    public static boolean isPdf(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    public static boolean isEpub(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".epub");
    }

    public static boolean isMobi(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mobi") || n.endsWith(".azw") || n.endsWith(".azw3");
    }

    public static boolean isOffice(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".doc") || n.endsWith(".docx");
    }

    public static boolean isPpt(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".pptx") || n.endsWith(".ppt");
    }

    public static boolean isText(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        String base = dot > 0 ? n.substring(0, dot) : n;
        if (TEXT_EXTS.contains(ext)) {
            return true;
        }
        return TEXT_NAMES.contains(n) || TEXT_NAMES.contains(base);
    }

    public static boolean isExcel(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".xlsx") || n.endsWith(".xls");
    }

    public static String previewCategory(String name) {
        if (isImage(name)) return "image";
        if (isAudio(name)) return "audio";
        if (isVideo(name)) return "video";
        if (isPdf(name)) return "pdf";
        if (isEpub(name)) return "epub";
        if (isMobi(name)) return "mobi";
        if (isExcel(name)) return "excel";
        if (isPpt(name)) return "ppt";
        if (isOffice(name)) return "office";
        if (isText(name)) return "text";
        return "";
    }

    private static final int EXCEL_MAX_ROWS = 1000;
    private static final int EXCEL_MAX_COLS = 50;
    private static final int PPT_MAX_SLIDES = 80;
    private static final long PPT_MAX_BYTES = 40L * 1024 * 1024;
    private static final double PPT_SCALE = 1.6;
    private static final Set<String> TEXT_EXTS = Set.of(
            "txt", "md", "markdown", "log", "html", "htm", "css", "js", "mjs", "cjs", "ts", "tsx",
            "jsx", "vue", "json", "xml", "yml", "yaml", "ini", "conf", "cfg", "properties", "env",
            "sql", "sh", "bash", "bat", "cmd", "ps1", "py", "java", "go", "rs", "c", "cpp", "h",
            "hpp", "cs", "php", "rb", "swift", "kt", "scala", "r", "lua", "toml", "csv", "tsv",
            "srt", "vtt", "diff", "patch", "gitignore"
    );
    private static final Set<String> TEXT_NAMES = Set.of("dockerfile", "makefile", "license", "readme");

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
