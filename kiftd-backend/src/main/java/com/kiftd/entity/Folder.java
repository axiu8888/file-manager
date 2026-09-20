package com.kiftd.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "folder",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_folder_parent_name",
                columnNames = {"folder_parent", "folder_name"}
        )
)
@Data
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

}
