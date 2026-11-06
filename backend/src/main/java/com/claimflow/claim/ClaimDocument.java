package com.claimflow.claim;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "claim_documents")
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    protected ClaimDocument() {}

    public ClaimDocument(Long claimId, String fileName, String contentType, long sizeBytes, String sha256,
                         String storageKey, String extractedText, Long uploadedBy) {
        this.claimId = claimId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.extractedText = extractedText;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public String getExtractedText() { return extractedText; }
    public Long getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
}
