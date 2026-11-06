package com.claimflow.document;

import com.claimflow.common.ApiException;
import com.claimflow.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

/** Stores files on a Docker volume shared by all API replicas. Keys are random, never user input. */
@Service
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(AppProperties props) throws IOException {
        this.root = Path.of(props.storage().uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(byte[] content, String extension) throws IOException {
        LocalDate d = LocalDate.now();
        String key = "%d/%02d/%s%s".formatted(d.getYear(), d.getMonthValue(), UUID.randomUUID(), extension);
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return key;
    }

    @Override
    public Resource load(String key) {
        Path p = resolve(key);
        if (!Files.exists(p)) throw ApiException.notFound("File");
        return new FileSystemResource(p);
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw ApiException.badRequest("Invalid file key");
        return p;
    }
}
