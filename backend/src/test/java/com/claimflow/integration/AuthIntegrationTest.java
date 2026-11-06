package com.claimflow.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerLoginAndMe() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@test.dev";
        var reg = mvc.perform(jsonBody(post("/api/auth/register"),
                        Map.of("email", email, "password", PASSWORD, "fullName", "New Customer")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = body(reg).get("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.dev";
        var req = Map.of("email", email, "password", PASSWORD, "fullName", "Dup");
        mvc.perform(jsonBody(post("/api/auth/register"), req)).andExpect(status().isCreated());
        mvc.perform(jsonBody(post("/api/auth/register"), req)).andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        var a = account(com.claimflow.user.Role.CUSTOMER);
        mvc.perform(jsonBody(post("/api/auth/login"), Map.of("email", a.user().getEmail(), "password", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsListFields() throws Exception {
        mvc.perform(jsonBody(post("/api/auth/register"), Map.of("email", "not-an-email", "password", "short", "fullName", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void protectedEndpointsNeedToken() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/claims").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
