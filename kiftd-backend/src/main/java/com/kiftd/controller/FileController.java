package com.kiftd.controller;

import com.kiftd.aop.HttpLoggingIgnore;
import com.kiftd.common.ApiResponse;
import com.kiftd.dto.FileDtos;
import com.kiftd.entity.FileNode;
import com.kiftd.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/check-upload")
    public ApiResponse<FileDtos.CheckUploadResponse> checkUpload(@RequestBody FileDtos.CheckUploadRequest req) {
        return ApiResponse.ok(fileService.checkUpload(req.folderId(), req.fileNames()));
    }

    @PostMapping("/upload")
    public ApiResponse<FileNode> upload(
            @RequestParam String folderId,
            @RequestParam(required = false) String uploadKey,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(fileService.upload(folderId, uploadKey, file, overwrite));
    }

    @PutMapping("/{fileId}")
    public ApiResponse<Void> rename(@PathVariable String fileId, @RequestBody FileDtos.RenameFileRequest req) {
        fileService.rename(fileId, req.newName());
        return ApiResponse.ok();
    }

    @PutMapping("/{fileId}/content")
    public ApiResponse<FileNode> saveText(
            @PathVariable String fileId,
            @HttpLoggingIgnore @RequestBody FileDtos.SaveTextRequest req) throws IOException {
        return ApiResponse.ok(fileService.saveTextContent(fileId, req == null ? "" : req.content()));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@PathVariable String fileId) throws IOException {
        fileService.delete(fileId);
        return ApiResponse.ok();
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody Map<String, List<String>> body) throws IOException {
        fileService.deleteBatch(body.get("fileIds"), body.get("folderIds"));
        return ApiResponse.ok();
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileId) throws IOException {
        return fileService.download(fileId);
    }

    @PostMapping("/confirm-move")
    public ApiResponse<FileDtos.ConfirmMoveResponse> confirmMove(@RequestBody FileDtos.MoveRequest req) {
        return ApiResponse.ok(fileService.confirmMove(req.targetFolderId(), req.fileIds(), req.folderIds()));
    }

    @PostMapping("/move")
    public ApiResponse<Void> move(@RequestBody FileDtos.MoveRequest req) throws IOException {
        fileService.moveOrCopy(req);
        return ApiResponse.ok();
    }

    @PostMapping("/zip")
    public ResponseEntity<StreamingResponseBody> zip(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("fileIds");
        StreamingResponseBody stream = fileService.zipDownload(ids);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=download.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }
}
