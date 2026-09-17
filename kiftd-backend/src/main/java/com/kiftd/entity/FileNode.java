package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_node")
public class FileNode {

    @Id
    @Column(name = "file_id", length = 64)
    private String fileId;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_size", nullable = false, length = 64)
    private String fileSize;

    @Column(name = "file_parent_folder", nullable = false, length = 64)
    private String fileParentFolder;

    @Column(name = "file_creation_date", nullable = false, length = 32)
    private String fileCreationDate;

    @Column(name = "file_creator", nullable = false, length = 128)
    private String fileCreator;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }
    public String getFileParentFolder() { return fileParentFolder; }
    public void setFileParentFolder(String fileParentFolder) { this.fileParentFolder = fileParentFolder; }
    public String getFileCreationDate() { return fileCreationDate; }
    public void setFileCreationDate(String fileCreationDate) { this.fileCreationDate = fileCreationDate; }
    public String getFileCreator() { return fileCreator; }
    public void setFileCreator(String fileCreator) { this.fileCreator = fileCreator; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
