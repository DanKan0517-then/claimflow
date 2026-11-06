package com.claimflow.integration;

import com.claimflow.policy.Policy;
import com.claimflow.policy.PolicyRepository;
import com.claimflow.policy.PolicyType;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Boots the whole app against real Postgres and Redis containers (same images as production).
 * Containers are started once and shared by every integration test class.
 */
@SpringBootTest(properties = {
        "app.ai.provider=rules",
        "app.ai.poll-interval-ms=200",
        "app.workflow.manager-approval-threshold=50000",
        "app.seed-demo=false"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("app.storage.upload-dir", () -> System.getProperty("java.io.tmpdir") + "/claimflow-test-uploads");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserService userService;
    @Autowired protected PolicyRepository policyRepository;

    protected static final String PASSWORD = "Passw0rd!x";

    protected record Account(User user, String token) {}

    protected Account account(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        User u = userService.create(email, PASSWORD, role.name() + " Tester", role, null);
        return new Account(u, login(email));
    }

    protected String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    protected Policy policy(User holder, String sumInsured, LocalDate start) {
        return policyRepository.save(new Policy("POL-T-" + UUID.randomUUID().toString().substring(0, 8), holder.getId(),
                PolicyType.HEALTH, new BigDecimal(sumInsured), new BigDecimal("1000"), start, start.plusYears(1)));
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, Account a) {
        return req.header("Authorization", "Bearer " + a.token());
    }

    protected MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder req, Object body) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
