package com.kiftd.dto;

import com.kiftd.entity.FileNode;
import com.kiftd.entity.Folder;

import java.util.List;

public final class FolderDtos {
    private FolderDtos() {}

    public record FolderViewDto(
            Folder folder,
            List<Folder> parentList,
            List<Folder> folderList,
            List<FileNode> fileList,
            String account,
            String authList,
            String publishTime,
            boolean allowDownload,
            int foldersOffset,
            int filesOffset,
            int selectStep
    ) {}

    public record RemainingViewDto(List<Folder> folderList, List<FileNode> fileList) {}

    public record NewFolderRequest(String parentId, String folderName, Integer constraint, Boolean getOrCreate) {}
    public record RenameFolderRequest(String folderId, String newName, Integer constraint) {}
    public record FolderCountDto(long folderCount, long fileCount, String totalSize) {}
}
