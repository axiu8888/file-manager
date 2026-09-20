package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "file_node")
@Data
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

}
