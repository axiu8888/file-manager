package com.kiftd.service;

import com.kiftd.common.BizException;
import com.kiftd.dto.FileDtos;
import com.kiftd.entity.FileNode;
import com.kiftd.entity.Folder;
import com.kiftd.repository.FileNodeRepository;
import com.kiftd.repository.FolderRepository;
import com.kiftd.security.AccountAuth;
import com.kiftd.util.IdUtil;
import com.kiftd.util.SecurityUtils;
import com.kiftd.util.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {

    private final FileNodeRepository fileNodeRepository;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final StorageService storageService;
    private final Map<String, String> uploadKeys = new ConcurrentHashMap<>();

    public FileService(FileNodeRepository fileNodeRepository, FolderRepository folderRepository,
                       FolderService folderService, StorageService storageService) {
        this.fileNodeRepository = fileNodeRepository;
        this.folderRepository = folderRepository;
        this.folderService = folderService;
        this.storageService = storageService;
    }

    public FileNode requireFile(String fileId) {
        return fileNodeRepository.findById(fileId)
                .orElseThrow(() -> new BizException("文件不存在"));
    }

    public FileDtos.CheckUploadResponse checkUpload(String folderId, List<String> names) {
        SecurityUtils.requireAuth(AccountAuth.UPLOAD_FILES);
        Folder folder = folderService.requireFolder(folderId);
        folderService.checkAccess(folder);
        List<String> overlaps = new ArrayList<>();
        for (String name : names) {
            if (fileNodeRepository.findByFileParentFolderAndFileName(folderId, name).isPresent()) {
                overlaps.add(name);
            }
        }
        String key = IdUtil.uuid();
        uploadKeys.put(key, folderId);
        return new FileDtos.CheckUploadResponse(overlaps.isEmpty(), overlaps, key);
    }

    @Transactional
    public FileNode upload(String folderId, String uploadKey, MultipartFile file, boolean overwrite) throws IOException {
        SecurityUtils.requireAuth(AccountAuth.UPLOAD_FILES);
        if (uploadKey != null && uploadKeys.containsKey(uploadKey)
                && !folderId.equals(uploadKeys.get(uploadKey))) {
            throw new BizException("上传凭证无效");
        }
        Folder folder = folderService.requireFolder(folderId);
        folderService.checkAccess(folder);
        String name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        var existing = fileNodeRepository.findByFileParentFolderAndFileName(folderId, name);
        if (existing.isPresent()) {
            if (!overwrite) {
                throw new BizException("文件已存在");
            }
            FileNode node = existing.get();
            storageService.deleteBlock(node.getFilePath());
            String path = storageService.saveNewBlock(file.getInputStream());
            node.setFilePath(path);
            node.setFileSize(String.valueOf(file.getSize()));
            node.setFileCreationDate(IdUtil.now());
            node.setFileCreator(SecurityUtils.currentUsername());
            return fileNodeRepository.save(node);
        }
        FileNode node = new FileNode();
        node.setFileId(IdUtil.uuid());
        node.setFileName(name);
        node.setFileSize(String.valueOf(file.getSize()));
        node.setFileParentFolder(folderId);
        node.setFileCreationDate(IdUtil.now());
        node.setFileCreator(SecurityUtils.currentUsername());
        node.setFilePath(storageService.saveNewBlock(file.getInputStream()));
        return fileNodeRepository.save(node);
    }

    @Transactional
    public void rename(String fileId, String newName) {
        SecurityUtils.requireAuth(AccountAuth.RENAME_FILE_OR_FOLDER);
        FileNode node = requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        fileNodeRepository.findByFileParentFolderAndFileName(node.getFileParentFolder(), newName)
                .filter(f -> !f.getFileId().equals(fileId))
                .ifPresent(f -> { throw new BizException("文件名已存在"); });
        node.setFileName(newName);
        fileNodeRepository.save(node);
    }

    @Transactional
    public void delete(String fileId) throws IOException {
        SecurityUtils.requireAuth(AccountAuth.DELETE_FILE_OR_FOLDER);
        FileNode node = requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        storageService.deleteBlock(node.getFilePath());
        fileNodeRepository.delete(node);
    }

    @Transactional
    public void deleteBatch(List<String> fileIds, List<String> folderIds) throws IOException {
        SecurityUtils.requireAuth(AccountAuth.DELETE_FILE_OR_FOLDER);
        if (fileIds != null) {
            for (String id : fileIds) {
                delete(id);
            }
        }
        if (folderIds != null) {
            for (String id : folderIds) {
                folderService.deleteFolder(id);
            }
        }
    }

    public ResponseEntity<Resource> download(String fileId) throws IOException {
        SecurityUtils.requireAuth(AccountAuth.DOWNLOAD_FILES);
        FileNode node = requireFile(fileId);
        folderService.checkAccess(folderService.requireFolder(node.getFileParentFolder()));
        Path path = storageService.resolveBlock(node.getFilePath());
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        String encoded = URLEncoder.encode(node.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(Files.size(path))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    public FileDtos.ConfirmMoveResponse confirmMove(String targetFolderId, List<String> fileIds, List<String> folderIds) {
        SecurityUtils.requireAuth(AccountAuth.MOVE_FILES);
        folderService.checkAccess(folderService.requireFolder(targetFolderId));
        List<String> conflictFiles = new ArrayList<>();
        List<String> conflictFolders = new ArrayList<>();
        if (fileIds != null) {
            for (String id : fileIds) {
                FileNode n = requireFile(id);
                if (fileNodeRepository.findByFileParentFolderAndFileName(targetFolderId, n.getFileName()).isPresent()) {
                    conflictFiles.add(n.getFileName());
                }
            }
        }
        if (folderIds != null) {
            for (String id : folderIds) {
                Folder f = folderService.requireFolder(id);
                if (folderRepository.findFirstByFolderParentAndFolderName(targetFolderId, f.getFolderName()).isPresent()) {
                    conflictFolders.add(f.getFolderName());
                }
            }
        }
        return new FileDtos.ConfirmMoveResponse(conflictFiles, conflictFolders);
    }

    @Transactional
    public void moveOrCopy(FileDtos.MoveRequest req) throws IOException {
        SecurityUtils.requireAuth(AccountAuth.MOVE_FILES);
        Folder target = folderService.requireFolder(req.targetFolderId());
        folderService.checkAccess(target);
        Map<String, String> strategy = req.conflictStrategy() == null ? Map.of() : req.conflictStrategy();

        if (req.fileIds() != null) {
            for (String id : req.fileIds()) {
                FileNode src = requireFile(id);
                var exist = fileNodeRepository.findByFileParentFolderAndFileName(req.targetFolderId(), src.getFileName());
                String action = strategy.getOrDefault(src.getFileName(), "cover");
                if (exist.isPresent()) {
                    if ("skip".equals(action)) {
                        continue;
                    }
                    if ("both".equals(action)) {
                        copyOrMoveFile(src, req.targetFolderId(), uniqueName(req.targetFolderId(), src.getFileName(), true), req.copy());
                        continue;
                    }
                    // cover
                    FileNode old = exist.get();
                    storageService.deleteBlock(old.getFilePath());
                    fileNodeRepository.delete(old);
                }
                copyOrMoveFile(src, req.targetFolderId(), src.getFileName(), req.copy());
            }
        }
        if (req.folderIds() != null) {
            for (String id : req.folderIds()) {
                Folder src = folderService.requireFolder(id);
                if (id.equals(req.targetFolderId()) || isDescendant(req.targetFolderId(), id)) {
                    throw new BizException("不能移动到自身或子目录");
                }
                var exist = folderRepository.findFirstByFolderParentAndFolderName(req.targetFolderId(), src.getFolderName());
                String action = strategy.getOrDefault(src.getFolderName(), "cover");
                if (exist.isPresent()) {
                    if ("skip".equals(action)) continue;
                    if ("cover".equals(action)) {
                        folderService.deleteFolder(exist.get().getFolderId());
                    }
                }
                String name = src.getFolderName();
                if (exist.isPresent() && "both".equals(action)) {
                    name = uniqueFolderName(req.targetFolderId(), name);
                }
                if (req.copy()) {
                    copyFolderRecursive(src, req.targetFolderId(), name);
                } else {
                    src.setFolderParent(req.targetFolderId());
                    src.setFolderName(name);
                    folderRepository.save(src);
                }
            }
        }
    }

    private boolean isDescendant(String maybeChild, String ancestorId) {
        Folder cur = folderRepository.findById(maybeChild).orElse(null);
        while (cur != null) {
            if (ancestorId.equals(cur.getFolderId())) return true;
            if (cur.getFolderParent() == null) break;
            cur = folderRepository.findById(cur.getFolderParent()).orElse(null);
        }
        return false;
    }

    private void copyOrMoveFile(FileNode src, String targetFolderId, String name, boolean copy) throws IOException {
        if (copy) {
            FileNode n = new FileNode();
            n.setFileId(IdUtil.uuid());
            n.setFileName(name);
            n.setFileSize(src.getFileSize());
            n.setFileParentFolder(targetFolderId);
            n.setFileCreationDate(IdUtil.now());
            n.setFileCreator(SecurityUtils.currentUsername());
            n.setFilePath(storageService.duplicateBlock(src.getFilePath()));
            fileNodeRepository.save(n);
        } else {
            src.setFileParentFolder(targetFolderId);
            src.setFileName(name);
            fileNodeRepository.save(src);
        }
    }

    private void copyFolderRecursive(Folder src, String targetParent, String name) throws IOException {
        Folder nf = new Folder();
        nf.setFolderId(IdUtil.uuid());
        nf.setFolderName(name);
        nf.setFolderParent(targetParent);
        nf.setFolderConstraint(src.getFolderConstraint());
        nf.setFolderCreationDate(IdUtil.now());
        nf.setFolderCreator(SecurityUtils.currentUsername());
        folderRepository.save(nf);
        for (FileNode file : fileNodeRepository.findByFileParentFolderOrderByFileNameAsc(src.getFolderId())) {
            copyOrMoveFile(file, nf.getFolderId(), file.getFileName(), true);
        }
        for (Folder child : folderRepository.findByFolderParentOrderByFolderNameAsc(src.getFolderId())) {
            copyFolderRecursive(child, nf.getFolderId(), child.getFolderName());
        }
    }

    private String uniqueName(String folderId, String name, boolean isFile) {
        String base = name;
        String ext = "";
        int idx = name.lastIndexOf('.');
        if (idx > 0) {
            base = name.substring(0, idx);
            ext = name.substring(idx);
        }
        int i = 1;
        String candidate = base + " (" + i + ")" + ext;
        while (fileNodeRepository.findByFileParentFolderAndFileName(folderId, candidate).isPresent()) {
            i++;
            candidate = base + " (" + i + ")" + ext;
        }
        return candidate;
    }

    private String uniqueFolderName(String parentId, String name) {
        int i = 1;
        String candidate = name + " (" + i + ")";
        while (folderRepository.findFirstByFolderParentAndFolderName(parentId, candidate).isPresent()) {
            i++;
            candidate = name + " (" + i + ")";
        }
        return candidate;
    }

    public List<FileNode> search(String keyword) {
        return fileNodeRepository.findByFileNameContainingIgnoreCase(keyword);
    }

    public StreamingResponseBody zipDownload(List<String> fileIds) {
        SecurityUtils.requireAuth(AccountAuth.DOWNLOAD_FILES);
        return out -> {
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                for (String id : fileIds) {
                    FileNode node = requireFile(id);
                    zos.putNextEntry(new ZipEntry(node.getFileName()));
                    Files.copy(storageService.resolveBlock(node.getFilePath()), zos);
                    zos.closeEntry();
                }
            }
        };
    }

    public Path resolvePath(FileNode node) {
        return storageService.resolveBlock(node.getFilePath());
    }
}
