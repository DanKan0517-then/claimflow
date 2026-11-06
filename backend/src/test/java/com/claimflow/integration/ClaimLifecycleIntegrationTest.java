package com.claimflow.integration;

import com.claimflow.policy.Policy;
import com.claimflow.user.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaimLifecycleIntegrationTest extends IntegrationTestBase {

    private Account customer;
    private Account adjuster;
    private Account manager;
    private Policy policy;

    @BeforeEach
    void setUp() throws Exception {
        customer = account(Role.CUSTOMER);
        adjuster = account(Role.ADJUSTER);
        manager = account(Role.MANAGER);
        policy = policy(customer.user(), "500000", LocalDate.now().minusMonths(6));
    }

    private long createClaim(String amount) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("policyId", policy.getId());
        req.put("title", "Hospital stay for surgery");
        req.put("description", "Admitted for appendix surgery, stayed three days. Bill attached.");
        req.put("incidentDate", LocalDate.now().minusDays(3).toString());
        req.put("claimedAmount", amount);
        var r = mvc.perform(jsonBody(auth(post("/api/claims"), customer), req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claim.status").value("DRAFT"))
                .andReturn();
        return body(r).at("/claim/id").asLong();
    }

    private void uploadBill(long claimId, String total) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bill.txt", "text/plain",
                ("City Hospital\nSurgery Rs. 30,000\nGrand Total: Rs. " + total).getBytes(StandardCharsets.UTF_8));
        mvc.perform(auth(multipart("/api/claims/{id}/documents", claimId).file(file), customer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textExtracted").value(true));
    }

    private JsonNode submitAndWaitForReview(long claimId) throws Exception {
        mvc.perform(auth(post("/api/claims/{id}/submit", claimId), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("AI_REVIEW"));
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250)).until(() ->
                "UNDER_REVIEW".equals(staffView(claimId).at("/claim/status").asText()));
        return staffView(claimId);
    }

    private JsonNode staffView(long claimId) throws Exception {
        return body(mvc.perform(auth(get("/api/claims/{id}", claimId), manager)).andReturn());
    }

    private JsonNode act(Account who, long claimId, Map<String, Object> action, int expectedStatus) throws Exception {
        var r = mvc.perform(jsonBody(auth(post("/api/claims/{id}/actions", claimId), who), action))
                .andExpect(status().is(expectedStatus)).andReturn();
        return body(r);
    }

    @Test
    void smallClaimGoesFromSubmissionToPayout() throws Exception {
        long id = createClaim("32000");
        uploadBill(id, "32,000");

        JsonNode reviewed = submitAndWaitForReview(id);
        assertThat(reviewed.at("/claim/fraudScore").isInt()).isTrue();
        assertThat(reviewed.at("/aiSummary").asText()).isNotBlank();
        assertThat(reviewed.at("/aiExtracted/billTotal").decimalValue()).isEqualByComparingTo("32000");
        assertThat(reviewed.at("/claim/assignedAdjusterId").isNull()).isFalse();

        // adjuster picks it up and approves (under the threshold, so no manager needed)
        act(adjuster, id, Map.of("action", "ASSIGN_TO_ME"), 200);
        JsonNode approved = act(adjuster, id, Map.of("action", "APPROVE", "approvedAmount", 30000), 200);
        assertThat(approved.at("/claim/status").asText()).isEqualTo("APPROVED");
        assertThat(approved.at("/claim/approvedAmount").decimalValue()).isEqualByComparingTo("30000");

        // adjuster can't mark paid, manager can
        act(adjuster, id, Map.of("action", "MARK_PAID"), 400);
        JsonNode paid = act(manager, id, Map.of("action", "MARK_PAID"), 200);
        assertThat(paid.at("/claim/status").asText()).isEqualTo("PAID");

        // customer sees the outcome but not the fraud internals
        JsonNode customerView = body(mvc.perform(auth(get("/api/claims/{id}", id), customer)).andReturn());
        assertThat(customerView.at("/claim/status").asText()).isEqualTo("PAID");
        assertThat(customerView.get("fraudFlags").isNull()).isTrue();
        assertThat(customerView.get("aiSummary").isNull()).isTrue();
        assertThat(customerView.get("allowedActions")).isEmpty();

        // full audit trail for staff
        JsonNode timeline = staffView(id).get("timeline");
        assertThat(timeline.findValuesAsText("action"))
                .contains("CREATED", "DOCUMENT_UPLOADED", "SUBMITTED", "AI_ANALYZED", "ASSIGNED", "APPROVED", "PAID");
    }

    @Test
    void bigClaimNeedsManagerApproval() throws Exception {
        long id = createClaim("120000");
        uploadBill(id, "1,20,000");
        submitAndWaitForReview(id);
        act(adjuster, id, Map.of("action", "ASSIGN_TO_ME"), 200);

        JsonNode pending = act(adjuster, id, Map.of("action", "APPROVE"), 200);
        assertThat(pending.at("/claim/status").asText()).isEqualTo("PENDING_APPROVAL");

        // the adjuster can't approve their own recommendation (four-eyes)
        act(adjuster, id, Map.of("action", "APPROVE"), 400);

        JsonNode approved = act(manager, id, Map.of("action", "APPROVE", "approvedAmount", 110000), 200);
        assertThat(approved.at("/claim/status").asText()).isEqualTo("APPROVED");
        assertThat(approved.at("/claim/approvedAmount").decimalValue()).isEqualByComparingTo("110000");
    }

    @Test
    void infoRequestRoundTrip() throws Exception {
        long id = createClaim("15000");
        submitAndWaitForReview(id);
        act(adjuster, id, Map.of("action", "ASSIGN_TO_ME"), 200);
        JsonNode r = act(adjuster, id, Map.of("action", "REQUEST_INFO", "reason", "Please upload the discharge summary"), 200);
        assertThat(r.at("/claim/status").asText()).isEqualTo("INFO_REQUESTED");

        uploadBill(id, "15,000");
        mvc.perform(jsonBody(auth(post("/api/claims/{id}/respond", id), customer), Map.of("message", "Uploaded it now")))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(15)).until(() ->
                "UNDER_REVIEW".equals(staffView(id).at("/claim/status").asText()));
        assertThat(staffView(id).at("/claim/assignedAdjusterId").asLong()).isEqualTo(adjuster.user().getId());
    }

    @Test
    void rejectNeedsAReason() throws Exception {
        long id = createClaim("9000");
        submitAndWaitForReview(id);
        act(manager, id, Map.of("action", "REJECT", "reason", "no"), 400);
        JsonNode r = act(manager, id, Map.of("action", "REJECT", "reason", "Treatment is excluded under section 4.2"), 200);
        assertThat(r.at("/claim/status").asText()).isEqualTo("REJECTED");
        assertThat(r.at("/decisionReason").asText()).contains("section 4.2");
    }

    @Test
    void staleVersionIsRejectedWithConflict() throws Exception {
        long id = createClaim("9000");
        JsonNode reviewed = submitAndWaitForReview(id);
        long version = reviewed.get("version").asLong();

        act(manager, id, Map.of("action", "ASSIGN_TO_ME", "expectedVersion", version), 200);
        // a second user still looking at the old version
        act(adjuster, id, Map.of("action", "APPROVE", "expectedVersion", version), 409);
    }

    @Test
    void validationAgainstPolicy() throws Exception {
        Map<String, Object> tooMuch = new LinkedHashMap<>();
        tooMuch.put("policyId", policy.getId());
        tooMuch.put("title", "Huge claim");
        tooMuch.put("description", "This is more than the sum insured on the policy.");
        tooMuch.put("incidentDate", LocalDate.now().minusDays(1).toString());
        tooMuch.put("claimedAmount", "900000");
        mvc.perform(jsonBody(auth(post("/api/claims"), customer), tooMuch)).andExpect(status().isBadRequest());

        Map<String, Object> beforePolicy = new LinkedHashMap<>(tooMuch);
        beforePolicy.put("claimedAmount", "1000");
        beforePolicy.put("incidentDate", LocalDate.now().minusYears(2).toString());
        mvc.perform(jsonBody(auth(post("/api/claims"), customer), beforePolicy)).andExpect(status().isBadRequest());

        Map<String, Object> future = new LinkedHashMap<>(beforePolicy);
        future.put("incidentDate", LocalDate.now().plusDays(3).toString());
        mvc.perform(jsonBody(auth(post("/api/claims"), customer), future))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.incidentDate").exists());
    }

    @Test
    void customersAreIsolatedAndCantDoStaffActions() throws Exception {
        long id = createClaim("5000");
        Account other = account(Role.CUSTOMER);

        mvc.perform(auth(get("/api/claims/{id}", id), other)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/claims"), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        act(customer, id, Map.of("action", "APPROVE"), 403);

        // staff can't file claims; adjusters can't issue policies
        mvc.perform(jsonBody(auth(post("/api/claims"), adjuster), Map.of("policyId", policy.getId())))
                .andExpect(status().isBadRequest()); // validation runs first
        mvc.perform(jsonBody(auth(post("/api/policies"), adjuster), Map.of(
                "holderId", customer.user().getId(), "type", "MOTOR", "sumInsured", 100000, "premium", 2000,
                "startDate", LocalDate.now().toString(), "endDate", LocalDate.now().plusYears(1).toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void fakeFileTypeIsRejected() throws Exception {
        long id = createClaim("5000");
        MockMultipartFile fake = new MockMultipartFile("file", "bill.pdf", "application/pdf", "<html>".getBytes());
        mvc.perform(auth(multipart("/api/claims/{id}/documents", id).file(fake), customer))
                .andExpect(status().isBadRequest());
        MockMultipartFile exe = new MockMultipartFile("file", "x.exe", "application/x-msdownload", new byte[]{1, 2, 3});
        mvc.perform(auth(multipart("/api/claims/{id}/documents", id).file(exe), customer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyticsForStaffOnly() throws Exception {
        mvc.perform(auth(get("/api/analytics/summary"), manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()").value(14));
        mvc.perform(auth(get("/api/analytics/summary"), customer)).andExpect(status().isForbidden());
    }
}
