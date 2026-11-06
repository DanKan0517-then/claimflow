package com.claimflow.document;

import com.claimflow.audit.AuditService;
import com.claimflow.claim.*;
import com.claimflow.common.ApiException;
import com.claimflow.security.AuthUser;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Service
public class DocumentService {

    private static final Map<String, String> ALLOWED = Map.of(
            "application/pdf", ".pdf",
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "text/plain", ".txt");
    private static final int MAX_DOCS_PER_CLAIM = 10;

    private final ClaimDocumentRepository documents;
    private final ClaimAccess access;
    private final StorageService storage;
    private final TextExtractor extractor;
    private final AuditService audit;

    public DocumentService(ClaimDocumentRepository documents, ClaimAccess access, StorageService storage,
                           TextExtractor extractor, AuditService audit) {
        this.documents = documents;
        this.access = access;
        this.storage = storage;
        this.extractor = extractor;
        this.audit = audit;
    }

    @Transactional
    public ClaimDocument upload(AuthUser user, Long claimId, MultipartFile file) throws IOException {
        Claim claim = access.loadForUser(claimId, user);
        boolean customerCanUpload = claim.getStatus() == ClaimStatus.DRAFT || claim.getStatus() == ClaimStatus.INFO_REQUESTED;
        if (!user.isStaff() && !customerCanUpload) {
            throw ApiException.badRequest("Documents can only be added while the claim is a draft or info is requested");
        }
        if (claim.getStatus().isClosed()) {
            throw ApiException.badRequest("Claim is closed");
        }
        if (file.isEmpty()) throw ApiException.badRequest("File is empty");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String ext = ALLOWED.get(contentType);
        if (ext == null) throw ApiException.badRequest("Only PDF, PNG, JPG and TXT files are allowed");
        if (documents.countByClaimId(claimId) >= MAX_DOCS_PER_CLAIM) {
            throw ApiException.badRequest("A claim can have at most " + MAX_DOCS_PER_CLAIM + " documents");
        }

        byte[] bytes = file.getBytes();
        verifyMagicBytes(bytes, contentType);
        String sha = sha256(bytes);
        String key = storage.store(bytes, ext);
        String text = extractor.extract(bytes, contentType);
        String name = sanitize(file.getOriginalFilename());

        ClaimDocument doc = documents.save(new ClaimDocument(claimId, name, contentType, bytes.length, sha, key, text, user.id()));
        audit.record(claimId, user.id(), "DOCUMENT_UPLOADED", null, null, name);
        return doc;
    }

    @Transactional(readOnly = true)
    public DownloadableFile download(AuthUser user, Long claimId, Long docId) {
        access.loadForUser(claimId, user);
        ClaimDocument doc = documents.findById(docId).filter(d -> d.getClaimId().equals(claimId))
                .orElseThrow(() -> ApiException.notFound("Document"));
        return new DownloadableFile(doc.getFileName(), doc.getContentType(), storage.load(doc.getStorageKey()));
    }

    public record DownloadableFile(String fileName, String contentType, Resource resource) {}

    /** Don't trust the browser's Content-Type: check the file actually starts like a PDF/PNG/JPEG. */
    static void verifyMagicBytes(byte[] b, String contentType) {
        boolean ok = switch (contentType) {
            case "application/pdf" -> b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            case "image/png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            default -> true;
        };
        if (!ok) throw ApiException.badRequest("File content doesn't match its type");
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) return "document";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
