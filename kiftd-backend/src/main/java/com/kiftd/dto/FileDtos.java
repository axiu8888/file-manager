package com.kiftd.dto;

import java.util.List;
import java.util.Map;

public final class FileDtos {
    private FileDtos() {}

    public record CheckUploadRequest(String folderId, List<String> fileNames) {}
    public record CheckUploadResponse(boolean ok, List<String> overlaps, String uploadKey) {}
    public record RenameFileRequest(String fileId, String newName) {}
    public record MoveRequest(String targetFolderId, List<String> fileIds, List<String> folderIds,
                              boolean copy, Map<String, String> conflictStrategy) {}
    public record ConfirmMoveResponse(List<String> conflictFiles, List<String> conflictFolders) {}
    public record PictureInfo(String fileId, String fileName, String url) {}
    public record PictureViewList(List<PictureInfo> pictureViewList, int index) {}
    public record AudioInfo(String fileId, String fileName, String url, String artist, String lrc) {}
    public record VideoInfo(String fileId, String fileName, boolean needTranscode) {}
}
