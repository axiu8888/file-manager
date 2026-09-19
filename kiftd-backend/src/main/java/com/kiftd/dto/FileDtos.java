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
    public record PictureInfo(String fileId, String fileName, String url, String fileCreationDate, String fileSize) {}
    public record PictureViewList(List<PictureInfo> pictureViewList, int index) {}
    public record AudioInfo(String fileId, String fileName, String url, String artist, String lrc, String fileCreationDate, String fileSize) {}
    public record VideoInfo(String fileId, String fileName, boolean needTranscode) {}
    public record VideoItem(String fileId, String fileName, String fileCreationDate, String fileSize) {}
    public record VideoViewList(List<VideoItem> videoViewList, int index) {}
    public record SiblingItem(String fileId, String fileName, String fileCreationDate, String fileSize) {}
    public record SiblingViewList(List<SiblingItem> items, int index, String category) {}
    public record ExcelSheet(String name, List<List<String>> rows, boolean truncated) {}
    public record ExcelPreview(String fileName, List<ExcelSheet> sheets) {}
    public record PptSlide(int index, String title) {}
    public record PptPreview(String fileName, List<PptSlide> slides) {}
    public record SaveTextRequest(String content) {}
}
