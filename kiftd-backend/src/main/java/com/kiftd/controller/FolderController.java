package com.kiftd.controller;

import com.kiftd.common.ApiResponse;
import com.kiftd.dto.FolderDtos;
import com.kiftd.entity.FileNode;
import com.kiftd.entity.Folder;
import com.kiftd.service.FileService;
import com.kiftd.service.FolderService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${kiftd.api-prefix:/api}/folders")
public class FolderController {

    private final FolderService folderService;
    private final FileService fileService;

    public FolderController(FolderService folderService, FileService fileService) {
        this.folderService = folderService;
        this.fileService = fileService;
    }

    @GetMapping("/view")
    public ApiResponse<FolderDtos.FolderViewDto> view(@RequestParam(defaultValue = "root") String fid) {
        return ApiResponse.ok(folderService.getFolderView(fid));
    }

    @GetMapping("/remaining")
    public ApiResponse<FolderDtos.RemainingViewDto> remaining(
            @RequestParam String fid,
            @RequestParam(defaultValue = "0") int folderOffset,
            @RequestParam(defaultValue = "0") int fileOffset) {
        return ApiResponse.ok(folderService.getRemaining(fid, folderOffset, fileOffset));
    }

    @PostMapping
    public ApiResponse<Folder> create(@RequestBody FolderDtos.NewFolderRequest req) {
        boolean getOrCreate = Boolean.TRUE.equals(req.getOrCreate());
        return ApiResponse.ok(folderService.createFolder(req.parentId(), req.folderName(), req.constraint(), getOrCreate));
    }

    @PutMapping("/{folderId}")
    public ApiResponse<Folder> rename(@PathVariable String folderId, @RequestBody FolderDtos.RenameFolderRequest req) {
        return ApiResponse.ok(folderService.renameFolder(folderId, req.newName(), req.constraint()));
    }

    @DeleteMapping("/{folderId}")
    public ApiResponse<Void> delete(@PathVariable String folderId) {
        folderService.deleteFolder(folderId);
        return ApiResponse.ok();
    }

    @GetMapping("/{folderId}/count")
    public ApiResponse<FolderDtos.FolderCountDto> count(@PathVariable String folderId) {
        return ApiResponse.ok(folderService.countContent(folderId));
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam String keyword) {
        List<Folder> folders = folderService.searchFolders(keyword);
        List<FileNode> files = fileService.search(keyword);
        Map<String, Object> map = new HashMap<>();
        map.put("folders", folders);
        map.put("files", files);
        return ApiResponse.ok(map);
    }
}
