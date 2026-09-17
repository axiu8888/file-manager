package com.kiftd.service;

import com.kiftd.common.BizException;
import com.kiftd.config.KiftdProperties;
import com.kiftd.dto.FolderDtos;
import com.kiftd.entity.FileNode;
import com.kiftd.entity.Folder;
import com.kiftd.repository.FileNodeRepository;
import com.kiftd.repository.FolderRepository;
import com.kiftd.security.AccountAuth;
import com.kiftd.security.UserPrincipal;
import com.kiftd.util.IdUtil;
import com.kiftd.util.SecurityUtils;
import com.kiftd.util.StorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileNodeRepository fileNodeRepository;
    private final StorageService storageService;
    private final KiftdProperties props;

    public FolderService(FolderRepository folderRepository, FileNodeRepository fileNodeRepository,
                         StorageService storageService, KiftdProperties props) {
        this.folderRepository = folderRepository;
        this.fileNodeRepository = fileNodeRepository;
        this.storageService = storageService;
        this.props = props;
    }

    public Folder requireFolder(String folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new BizException("文件夹不存在"));
    }

    public void checkAccess(Folder folder) {
        int c = folder.getFolderConstraint() == null ? 0 : folder.getFolderConstraint();
        UserPrincipal user = SecurityUtils.currentUserOrNull();
        if (c >= 1 && user == null) {
            throw new BizException(401, "mustLogin");
        }
        if (c >= 2 && user == null) {
            throw new BizException(403, "notAccess");
        }
    }

    public FolderDtos.FolderViewDto getFolderView(String fid) {
        String folderId = (fid == null || fid.isBlank()) ? "root" : fid;
        Folder folder = requireFolder(folderId);
        checkAccess(folder);

        int step = props.selectStep();
        long folderTotal = folderRepository.countByFolderParent(folderId);
        long fileTotal = fileNodeRepository.countByFileParentFolder(folderId);

        List<Folder> folders = folderRepository.findByFolderParentOrderByFolderNameAsc(
                folderId, PageRequest.of(0, step));
        List<FileNode> files = fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(
                folderId, PageRequest.of(0, step));

        UserPrincipal user = SecurityUtils.currentUserOrNull();
        return new FolderDtos.FolderViewDto(
                folder,
                buildParentList(folder),
                folders,
                files,
                user == null ? null : user.getUsername(),
                user == null ? "" : String.join(",", user.getAuthorities().stream().map(Object::toString).toList()),
                IdUtil.now(),
                true,
                (int) folderTotal,
                (int) fileTotal,
                step
        );
    }

    public FolderDtos.RemainingViewDto getRemaining(String fid, int folderOffset, int fileOffset) {
        Folder folder = requireFolder(fid);
        checkAccess(folder);
        int step = props.selectStep();
        List<Folder> folders = folderOffset >= folderRepository.countByFolderParent(fid)
                ? List.of()
                : folderRepository.findByFolderParentOrderByFolderNameAsc(fid, PageRequest.of(folderOffset / step, step));
        // simpler: load all remaining by skipping
        List<Folder> allFolders = folderRepository.findByFolderParentOrderByFolderNameAsc(fid);
        List<FileNode> allFiles = fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(fid);
        List<Folder> remFolders = folderOffset < allFolders.size()
                ? allFolders.subList(Math.min(folderOffset, allFolders.size()), allFolders.size())
                : List.of();
        List<FileNode> remFiles = fileOffset < allFiles.size()
                ? allFiles.subList(Math.min(fileOffset, allFiles.size()), allFiles.size())
                : List.of();
        return new FolderDtos.RemainingViewDto(remFolders, remFiles);
    }

    private List<Folder> buildParentList(Folder folder) {
        List<Folder> list = new ArrayList<>();
        Folder cur = folder;
        while (cur != null) {
            list.add(cur);
            if (cur.getFolderParent() == null || cur.getFolderParent().isBlank()) {
                break;
            }
            cur = folderRepository.findById(cur.getFolderParent()).orElse(null);
        }
        Collections.reverse(list);
        return list;
    }

    /**
     * Idempotent get-or-create for concurrent folder uploads.
     * No outer {@code @Transactional}: catching {@link DataIntegrityViolationException}
     * inside the same TX would mark the session rollback-only.
     */
    public Folder createFolder(String parentId, String name, Integer constraint) {
        SecurityUtils.requireAuth(AccountAuth.CREATE_NEW_FOLDER);
        Folder parent = requireFolder(parentId);
        checkAccess(parent);
        var existing = folderRepository.findFirstByFolderParentAndFolderName(parentId, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        int c = constraint == null ? parent.getFolderConstraint() : constraint;
        if (c < parent.getFolderConstraint()) {
            c = parent.getFolderConstraint();
        }
        Folder folder = new Folder();
        folder.setFolderId(IdUtil.uuid());
        folder.setFolderName(name);
        folder.setFolderParent(parentId);
        folder.setFolderConstraint(c);
        folder.setFolderCreationDate(IdUtil.now());
        folder.setFolderCreator(SecurityUtils.currentUsername());
        try {
            return folderRepository.saveAndFlush(folder);
        } catch (DataIntegrityViolationException e) {
            return folderRepository.findFirstByFolderParentAndFolderName(parentId, name)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public Folder renameFolder(String folderId, String newName, Integer constraint) {
        SecurityUtils.requireAuth(AccountAuth.RENAME_FILE_OR_FOLDER);
        Folder folder = requireFolder(folderId);
        if ("root".equals(folderId)) {
            throw new BizException("根目录不可重命名");
        }
        checkAccess(folder);
        folderRepository.findFirstByFolderParentAndFolderName(folder.getFolderParent(), newName)
                .filter(f -> !f.getFolderId().equals(folderId))
                .ifPresent(f -> { throw new BizException("文件夹名称已存在"); });
        folder.setFolderName(newName);
        if (constraint != null) {
            folder.setFolderConstraint(constraint);
        }
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String folderId) {
        SecurityUtils.requireAuth(AccountAuth.DELETE_FILE_OR_FOLDER);
        if ("root".equals(folderId)) {
            throw new BizException("根目录不可删除");
        }
        Folder folder = requireFolder(folderId);
        checkAccess(folder);
        deleteFolderRecursive(folderId);
    }

    private void deleteFolderRecursive(String folderId) {
        for (Folder child : folderRepository.findByFolderParentOrderByFolderNameAsc(folderId)) {
            deleteFolderRecursive(child.getFolderId());
        }
        for (FileNode file : fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(folderId)) {
            try {
                storageService.deleteBlock(file.getFilePath());
            } catch (IOException ignored) {
            }
            fileNodeRepository.delete(file);
        }
        folderRepository.deleteById(folderId);
    }

    public FolderDtos.FolderCountDto countContent(String folderId) {
        Folder folder = requireFolder(folderId);
        checkAccess(folder);
        long folders = 0;
        long files = 0;
        long size = 0;
        List<String> queue = new ArrayList<>();
        queue.add(folderId);
        while (!queue.isEmpty()) {
            String id = queue.remove(0);
            folders += folderRepository.countByFolderParent(id);
            for (Folder f : folderRepository.findByFolderParentOrderByFolderNameAsc(id)) {
                queue.add(f.getFolderId());
            }
            List<FileNode> fileList = fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(id);
            files += fileList.size();
            for (FileNode n : fileList) {
                try {
                    size += Long.parseLong(n.getFileSize());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new FolderDtos.FolderCountDto(folders, files, formatSize(size));
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public List<Folder> searchFolders(String keyword) {
        return folderRepository.findByFolderNameContainingIgnoreCase(keyword);
    }
}
