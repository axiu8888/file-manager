package com.kiftd.repository;

import com.kiftd.entity.FileNode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileNodeRepository extends JpaRepository<FileNode, String> {
    List<FileNode> findByFileParentFolderOrderByFileNameAsc(String parentId);
    List<FileNode> findByFileParentFolderOrderByFileNameAsc(String parentId, Pageable pageable);
    long countByFileParentFolder(String parentId);
    Optional<FileNode> findByFileParentFolderAndFileName(String parentId, String fileName);
    List<FileNode> findByFileNameContainingIgnoreCase(String keyword);
    List<FileNode> findByFilePath(String filePath);
}
