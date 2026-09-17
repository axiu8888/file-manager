package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "folder",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_folder_parent_name",
                columnNames = {"folder_parent", "folder_name"}
        )
)
public class Folder {

    @Id
    @Column(name = "folder_id", length = 64)
    private String folderId;

    @Column(name = "folder_name", nullable = false)
    private String folderName;

    @Column(name = "folder_creation_date", nullable = false, length = 32)
    private String folderCreationDate;

    @Column(name = "folder_creator", nullable = false, length = 128)
    private String folderCreator;

    @Column(name = "folder_parent", length = 64)
    private String folderParent;

    @Column(name = "folder_constraint", nullable = false)
    private Integer folderConstraint = 0;

    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public String getFolderCreationDate() { return folderCreationDate; }
    public void setFolderCreationDate(String folderCreationDate) { this.folderCreationDate = folderCreationDate; }
    public String getFolderCreator() { return folderCreator; }
    public void setFolderCreator(String folderCreator) { this.folderCreator = folderCreator; }
    public String getFolderParent() { return folderParent; }
    public void setFolderParent(String folderParent) { this.folderParent = folderParent; }
    public Integer getFolderConstraint() { return folderConstraint; }
    public void setFolderConstraint(Integer folderConstraint) { this.folderConstraint = folderConstraint; }
}
