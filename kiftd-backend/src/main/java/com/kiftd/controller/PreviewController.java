package com.kiftd.controller;

import com.kiftd.aop.HttpLoggingIgnore;
import com.kiftd.common.ApiResponse;
import com.kiftd.entity.FileNode;
import com.kiftd.service.FileService;
import com.kiftd.service.PreviewService;
import com.kiftd.util.ContentDispositionUtil;
import com.kiftd.util.StorageService;
import com.kiftd.dto.FileDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("${kiftd.api-prefix:/api}/preview")
public class PreviewController {

    private final PreviewService previewService;
    private final FileService fileService;
    private final StorageService storageService;

    public PreviewController(PreviewService previewService, FileService fileService, StorageService storageService) {
        this.previewService = previewService;
        this.fileService = fileService;
        this.storageService = storageService;
    }

    @GetMapping("/pictures")
    public ApiResponse<FileDtos.PictureViewList> pictures(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.pictures(fileId));
    }

    @GetMapping("/audios")
    public ApiResponse<List<FileDtos.AudioInfo>> audios(@RequestParam String folderId) {
        return ApiResponse.ok(previewService.audios(folderId));
    }

    @GetMapping("/video")
    public ApiResponse<FileDtos.VideoInfo> video(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.video(fileId));
    }

    @GetMapping("/videos")
    public ApiResponse<FileDtos.VideoViewList> videos(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.videos(fileId));
    }

    @GetMapping("/siblings")
    public ApiResponse<FileDtos.SiblingViewList> siblings(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.siblings(fileId));
    }

    @GetMapping("/excel")
    public ApiResponse<FileDtos.ExcelPreview> excel(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.excelPreview(fileId));
    }

    @GetMapping("/ppt")
    public ApiResponse<FileDtos.PptPreview> ppt(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.pptPreview(fileId));
    }

    @HttpLoggingIgnore
    @GetMapping("/ppt-slide/{fileId}/{index}")
    public ResponseEntity<byte[]> pptSlide(@PathVariable String fileId, @PathVariable int index) {
        byte[] png = previewService.pptSlidePng(fileId, index);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=120")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @GetMapping("/transcode-status")
    public ApiResponse<String> transcode(@RequestParam String fileId) {
        return ApiResponse.ok(previewService.transcodeStatus(fileId));
    }

    @HttpLoggingIgnore
    @GetMapping("/resource/{fileId}")
    public ResponseEntity<StreamingResponseBody> resource(@PathVariable String fileId,
                                                          HttpServletRequest request) throws IOException {
        FileNode node = fileService.requireFile(fileId);
        Path path = storageService.resolveBlock(node.getFilePath());
        long fileLength = Files.size(path);
        String contentType = probeContentType(node.getFileName());
        String range = request.getHeader(HttpHeaders.RANGE);

        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            long start = Long.parseLong(parts[0]);
            long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : fileLength - 1;
            if (end >= fileLength) {
                end = fileLength - 1;
            }
            long contentLength = end - start + 1;
            long finalStart = start;
            long finalEnd = end;
            StreamingResponseBody body = outputStream -> {
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                    raf.seek(finalStart);
                    byte[] buf = new byte[8192];
                    long remaining = finalEnd - finalStart + 1;
                    while (remaining > 0) {
                        int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                        if (read < 0) {
                            break;
                        }
                        outputStream.write(buf, 0, read);
                        remaining -= read;
                    }
                } catch (IOException ex) {
                    // 客户端中断（拖动进度条/关闭页面）时忽略
                    if (!isClientAbort(ex)) {
                        throw ex;
                    }
                }
            };
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .body(body);
        }

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = Files.newInputStream(path)) {
                in.transferTo(outputStream);
            } catch (IOException ex) {
                if (!isClientAbort(ex)) {
                    throw ex;
                }
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength))
                .body(body);
    }

    @GetMapping({"/txt-pdf/{fileId}", "/txt-pdf/{fileId}/{fileName}"})
    public ResponseEntity<StreamingResponseBody> txtPdf(
            @PathVariable String fileId,
            @PathVariable(required = false) String fileName) {
        FileNode node = fileService.requireFile(fileId);
        String display = displayName(fileName, stripExt(node.getFileName()) + ".pdf");
        StreamingResponseBody body = out -> previewService.writeTxtAsPdf(fileId, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.inline(display))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @GetMapping({"/pdf/{fileId}", "/pdf/{fileId}/{fileName}"})
    public ResponseEntity<StreamingResponseBody> pdf(
            @PathVariable String fileId,
            @PathVariable(required = false) String fileName) {
        FileNode node = fileService.requireFile(fileId);
        String display = displayName(fileName, node.getFileName());
        StreamingResponseBody body = out -> previewService.writePdfDirect(fileId, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.inline(display))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @GetMapping({"/office-pdf/{fileId}", "/office-pdf/{fileId}/{fileName}"})
    public ResponseEntity<StreamingResponseBody> officePdf(
            @PathVariable String fileId,
            @PathVariable(required = false) String fileName) {
        FileNode node = fileService.requireFile(fileId);
        String display = displayName(fileName, stripExt(node.getFileName()) + ".pdf");
        StreamingResponseBody body = out -> previewService.writeOfficeAsPdf(fileId, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.inline(display))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    private String probeContentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".epub")) return "application/epub+zip";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html;charset=UTF-8";
        if (n.endsWith(".css")) return "text/css;charset=UTF-8";
        if (n.endsWith(".js") || n.endsWith(".mjs") || n.endsWith(".cjs")) return "text/javascript;charset=UTF-8";
        if (n.endsWith(".json")) return "application/json;charset=UTF-8";
        if (n.endsWith(".xml")) return "application/xml;charset=UTF-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".txt") || n.endsWith(".log")
                || n.endsWith(".yml") || n.endsWith(".yaml") || n.endsWith(".ini") || n.endsWith(".conf")
                || n.endsWith(".csv") || n.endsWith(".ts") || n.endsWith(".tsx") || n.endsWith(".jsx")
                || n.endsWith(".vue") || n.endsWith(".java") || n.endsWith(".py") || n.endsWith(".go")
                || n.endsWith(".sql") || n.endsWith(".sh") || n.endsWith(".bat") || n.endsWith(".properties")) {
            return "text/plain;charset=UTF-8";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String displayName(String fromPath, String fallback) {
        if (fromPath == null || fromPath.isBlank()) {
            return fallback;
        }
        return fromPath;
    }

    private static boolean isClientAbort(Throwable e) {
        while (e != null) {
            String name = e.getClass().getName();
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (name.contains("ClientAbortException")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset")
                    || msg.contains("Abort")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
