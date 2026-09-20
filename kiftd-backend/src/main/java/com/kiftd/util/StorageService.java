package com.kiftd.util;

import com.kiftd.config.KiftdProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class StorageService {

    private final Path root;
    private final Path temp;
    private final Path thumbs;

    public StorageService(KiftdProperties props) {
        this.root = Path.of(props.storage().root()).toAbsolutePath().normalize();
        this.temp = Path.of(props.storage().temp()).toAbsolutePath().normalize();
        Path parent = root.getParent();
        this.thumbs = (parent != null ? parent.resolve("thumbs") : root.resolve("thumbs")).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        ensureDirectory(root);
        ensureDirectory(temp);
        ensureDirectory(thumbs);
    }

    /**
     * Docker/WSL 把 Windows 目录 bind 到容器后，路径往往已存在且不是标准 POSIX 目录。
     * {@link Files#createDirectories} 内部用 NOFOLLOW_LINKS 判断，会误报 FileAlreadyExistsException。
     */
    private static void ensureDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            if (!Files.isDirectory(dir)) {
                throw new IOException("路径已存在但不是目录: " + dir.toAbsolutePath(), e);
            }
        }
    }

    public String saveNewBlock(InputStream in) throws IOException {
        String name = UUID.randomUUID().toString().replace("-", "");
        Path target = root.resolve(name);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public Path resolveBlock(String filePath) {
        Path p = root.resolve(filePath).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("非法路径");
        }
        return p;
    }

    public void deleteBlock(String filePath) throws IOException {
        Path p = resolveBlock(filePath);
        Files.deleteIfExists(p);
    }

    public void copyBlock(String srcPath, OutputStream out) throws IOException {
        Files.copy(resolveBlock(srcPath), out);
    }

    public String duplicateBlock(String srcPath) throws IOException {
        String name = UUID.randomUUID().toString().replace("-", "");
        Files.copy(resolveBlock(srcPath), root.resolve(name));
        return name;
    }

    public Path tempFile(String suffix) throws IOException {
        return Files.createTempFile(temp, "kiftd-", suffix);
    }

    public Path resolveThumb(String fileName) {
        Path p = thumbs.resolve(fileName).normalize();
        if (!p.startsWith(thumbs)) {
            throw new IllegalArgumentException("非法缩略图路径");
        }
        return p;
    }

    public Path getThumbsDir() {
        return thumbs;
    }

    public Path getRoot() {
        return root;
    }
}
