package com.kiftd.util;

import com.kiftd.config.KiftdProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
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

    public StorageService(KiftdProperties props) {
        this.root = Path.of(props.storage().root()).toAbsolutePath().normalize();
        this.temp = Path.of(props.storage().temp()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(temp);
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

    public Path getRoot() {
        return root;
    }
}
