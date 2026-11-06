package com.claimflow.document;

import org.springframework.core.io.Resource;

import java.io.IOException;

/** Abstraction over file storage so local disk can be swapped for S3/OCI Object Storage later. */
public interface StorageService {
    String store(byte[] content, String extension) throws IOException;

    Resource load(String key);
}
