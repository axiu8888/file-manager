package com.kiftd.repository;

import com.kiftd.entity.Folder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, String> {
    List<Folder> findByFolderParentOrderByFolderNameAsc(String parent);
    List<Folder> findByFolderParentOrderByFolderNameAsc(String parent, Pageable pageable);
    long countByFolderParent(String parent);
    Optional<Folder> findFirstByFolderParentAndFolderName(String parent, String name);
    List<Folder> findByFolderNameContainingIgnoreCase(String keyword);
    List<Folder> findByFolderParentIsNull();
}
