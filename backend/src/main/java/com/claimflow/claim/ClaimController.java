package com.claimflow.claim;

import com.claimflow.claim.ClaimDtos.*;
import com.claimflow.common.PageResponse;
import com.claimflow.document.DocumentService;
import com.claimflow.document.DocumentService.DownloadableFile;
import com.claimflow.policy.PolicyType;
import com.claimflow.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "claimedAmount", "fraudScore", "slaDueAt");

    private final ClaimService claimService;
    private final ClaimWorkflowService workflow;
    private final DocumentService documentService;
    private final ClaimMapper mapper;

    public ClaimController(ClaimService claimService, ClaimWorkflowService workflow, DocumentService documentService,
                           ClaimMapper mapper) {
        this.claimService = claimService;
        this.workflow = workflow;
        this.documentService = documentService;
        this.mapper = mapper;
    }

    @GetMapping
    public PageResponse<ClaimSummaryDto> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) ClaimStatus status,
                                              @RequestParam(required = false) PolicyType type,
                                              @RequestParam(required = false, defaultValue = "all") String scope,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Integer minFraud,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "createdAt") String sort,
                                              @RequestParam(defaultValue = "desc") String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(direction, sortField).and(Sort.by(Sort.Direction.DESC, "id")));
        return claimService.list(user, status, type, scope, q, minFraud, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDetailDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateClaimRequest req) {
        return claimService.create(user, req);
    }

    @GetMapping("/{id}")
    public ClaimDetailDto get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return claimService.detail(user, id);
    }

    @PostMapping("/{id}/submit")
    public ClaimDetailDto submit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return claimService.submit(user, id);
    }

    @PostMapping("/{id}/respond")
    public ClaimDetailDto respond(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody RespondRequest req) {
        return claimService.respond(user, id, req);
    }

    @PostMapping("/{id}/actions")
    public ClaimDetailDto act(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                              @Valid @RequestBody ActionRequest req) {
        workflow.act(user, id, req);
        return claimService.detail(user, id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto comment(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                              @Valid @RequestBody CommentRequest req) {
        return claimService.comment(user, id, req);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                              @RequestParam("file") MultipartFile file) throws IOException {
        return mapper.document(documentService.upload(user, id, file));
    }

    @GetMapping("/{id}/documents/{docId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                             @PathVariable Long docId) {
        DownloadableFile f = documentService.download(user, id, docId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.resource());
    }
}
