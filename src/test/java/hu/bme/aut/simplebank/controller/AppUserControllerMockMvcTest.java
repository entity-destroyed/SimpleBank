package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.user.CreateUserRequest;
import hu.bme.aut.simplebank.controller.dto.user.UpdateProfileRequest;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import hu.bme.aut.simplebank.util.MockMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AppUserControllerMockMvcTest {

    private static final String ADMIN_EMAIL = "admin-test@bank.local";
    private static final String ADMIN_PASSWORD = "AdminPass123";
    private static final String CLIENT_EMAIL = "client-test@bank.local";
    private static final String CLIENT_PASSWORD = "ClientPass123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private Long clientId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcTestSupport.buildMockMvc(context);
        MockMvcTestSupport.persistUser(userRepository, passwordEncoder,
                "Test Admin", ADMIN_EMAIL, ADMIN_PASSWORD, AppUser.Role.ADMIN);
        AppUser client = MockMvcTestSupport.persistUser(userRepository, passwordEncoder,
                "Test Client", CLIENT_EMAIL, CLIENT_PASSWORD, AppUser.Role.CLIENT);
        clientId = client.getId();
    }

    private String login(String email, String password) throws Exception {
        return MockMvcTestSupport.bearerToken(mockMvc, objectMapper, email, password);
    }

    @Test
    void adminCanRegisterNewUser() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        CreateUserRequest req = new CreateUserRequest(
                "Brand New", "brand-new@bank.local", "secretPass1", AppUser.Role.CLIENT);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("brand-new@bank.local"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void clientCannotRegister() throws Exception {
        String clientToken = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        CreateUserRequest req = new CreateUserRequest(
                "Sneaky", "sneaky@bank.local", "secretPass1", AppUser.Role.CLIENT);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationErrorReturns422WithFieldDetails() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String invalidBody = """
                {"name":"","email":"not-an-email","password":"short","role":null}
                """;

        mockMvc.perform(post("/api/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        CreateUserRequest dup = new CreateUserRequest(
                "Dup", CLIENT_EMAIL, "secretPass1", AppUser.Role.CLIENT);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void clientCanGetOwnProfile() throws Exception {
        String clientToken = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CLIENT_EMAIL))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void clientCanUpdateOwnProfile() throws Exception {
        String clientToken = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        UpdateProfileRequest req = new UpdateProfileRequest("Renamed", CLIENT_EMAIL);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void adminCanFetchUserById() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/users/" + clientId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.email").value(CLIENT_EMAIL));
    }

    @Test
    void unknownPathReturns404FromHandler() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/users/this-is-not-a-real-route/extra")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void clientCannotFetchAnotherUserById() throws Exception {
        String clientToken = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(get("/api/users/" + clientId)
                        .header("Authorization", clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void adminCanDeleteUser() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(delete("/api/users/" + clientId)
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminDeletingMissingUserReturns404() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(delete("/api/users/99999")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
