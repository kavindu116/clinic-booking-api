package lk.kavindu.clinic.auth;

import com.fasterxml.jackson.databind.JsonNode;
import lk.kavindu.clinic.auth.dto.LoginRequest;
import lk.kavindu.clinic.auth.dto.RefreshRequest;
import lk.kavindu.clinic.auth.dto.RegisterRequest;
import lk.kavindu.clinic.support.AbstractIntegrationTest;
import lk.kavindu.clinic.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIT extends AbstractIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String ME = "/api/v1/auth/me";

    @Test
    @DisplayName("register: creates a PATIENT account and returns tokens")
    void registerCreatesPatient() throws Exception {
        var request = new RegisterRequest("kavindu@example.com", "Str0ngPass1",
                "Kavindu Perera", "+94771234567");

        String body = mockMvc.perform(post(REGISTER)
                        .contentType(APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("kavindu@example.com"))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("passwordHash");

        var saved = userRepository.findByEmailIgnoreCase("kavindu@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
        assertThat(saved.getPasswordHash()).startsWith("$2a$").doesNotContain("Str0ngPass1");
    }

    @Test
    @DisplayName("register: rejects a duplicate email with 409")
    void registerRejectsDuplicateEmail() throws Exception {
        var request = new RegisterRequest("dupe@example.com", "Str0ngPass1", "First User", null);

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("register: returns field-level errors for a weak password")
    void registerValidatesPassword() throws Exception {
        var request = new RegisterRequest("weak@example.com", "short", "Weak User", null);

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("login: wrong password returns the same generic message as unknown email")
    void loginDoesNotLeakAccountExistence() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                .content(json(new RegisterRequest("real@example.com", "Str0ngPass1", "Real User", null))));

        String wrongPassword = mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("real@example.com", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post(LOGIN).contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest("ghost@example.com", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(node(wrongPassword).get("message").asText())
                .isEqualTo(node(unknownEmail).get("message").asText());
    }

    @Test
    @DisplayName("me: requires a valid bearer token")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        mockMvc.perform(get(ME).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("me: returns the authenticated user for a valid token")
    void meReturnsCurrentUser() throws Exception {
        String response = mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("me@example.com", "Str0ngPass1", "Me User", null))))
                .andReturn().getResponse().getContentAsString();

        String accessToken = node(response).get("accessToken").asText();

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    @DisplayName("refresh: rotates the token and rejects reuse of the old one")
    void refreshRotatesTokens() throws Exception {
        String response = mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(json(new RegisterRequest("rotate@example.com", "Str0ngPass1", "Rotate User", null))))
                .andReturn().getResponse().getContentAsString();

        String oldRefresh = node(response).get("refreshToken").asText();

        String refreshed = mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(oldRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String newRefresh = node(refreshed).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // Parana eka aayemath use karanna baa
        mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(oldRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // Reuse detect unama okkoma tokens revoke wenawa — aluth ekath weda karanne naa
        mockMvc.perform(post(REFRESH).contentType(APPLICATION_JSON)
                        .content(json(new RefreshRequest(newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode node(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
