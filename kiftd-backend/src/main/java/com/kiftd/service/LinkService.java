package com.kiftd.service;

import com.kiftd.common.BizException;
import com.kiftd.entity.DownloadKey;
import com.kiftd.entity.FileChain;
import com.kiftd.entity.FileNode;
import com.kiftd.repository.DownloadKeyRepository;
import com.kiftd.repository.FileChainRepository;
import com.kiftd.security.AccountAuth;
import com.kiftd.util.ContentDispositionUtil;
import com.kiftd.util.IdUtil;
import com.kiftd.util.SecurityUtils;
import com.kiftd.util.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class LinkService {

    private final FileChainRepository fileChainRepository;
    private final DownloadKeyRepository downloadKeyRepository;
    private final FileService fileService;
    private final StorageService storageService;

    public LinkService(FileChainRepository fileChainRepository, DownloadKeyRepository downloadKeyRepository,
                       FileService fileService, StorageService storageService) {
        this.fileChainRepository = fileChainRepository;
        this.downloadKeyRepository = downloadKeyRepository;
        this.fileService = fileService;
        this.storageService = storageService;
    }

    @Transactional
    public String createChain(String fileId) {
        SecurityUtils.requireAuth(AccountAuth.DOWNLOAD_FILES);
        fileService.requireAccessibleFile(fileId);
        FileChain chain = new FileChain();
        chain.setChainKey(IdUtil.uuid());
        chain.setFileId(fileId);
        chain.setExpireAt(Instant.now().plus(30, ChronoUnit.DAYS));
        fileChainRepository.save(chain);
        return chain.getChainKey();
    }

    @Transactional
    public String createDownloadKey(String fileId) {
        SecurityUtils.requireAuth(AccountAuth.DOWNLOAD_FILES);
        fileService.requireAccessibleFile(fileId);
        DownloadKey key = new DownloadKey();
        key.setDownloadKey(IdUtil.uuid());
        key.setFileId(fileId);
        key.setExpireAt(Instant.now().plus(1, ChronoUnit.DAYS));
        downloadKeyRepository.save(key);
        return key.getDownloadKey();
    }

    public ResponseEntity<Resource> downloadByChain(String chainKey) throws IOException {
        FileChain chain = fileChainRepository.findById(chainKey)
                .orElseThrow(() -> new BizException("直链无效"));
        if (chain.getExpireAt() != null && chain.getExpireAt().isBefore(Instant.now())) {
            throw new BizException("直链已过期");
        }
        return openFile(chain.getFileId());
    }

    public ResponseEntity<Resource> downloadByKey(String downloadKey) throws IOException {
        DownloadKey key = downloadKeyRepository.findById(downloadKey)
                .orElseThrow(() -> new BizException("下载密钥无效"));
        if (key.getExpireAt() != null && key.getExpireAt().isBefore(Instant.now())) {
            throw new BizException("下载密钥已过期");
        }
        return openFile(key.getFileId());
    }

    private ResponseEntity<Resource> openFile(String fileId) throws IOException {
        FileNode node = fileService.requireFile(fileId);
        Path path = storageService.resolveBlock(node.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw new BizException("文件数据不存在或已损坏");
        }
        long size = Files.size(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtil.attachment(node.getFileName()))
                .contentLength(size)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }
}
