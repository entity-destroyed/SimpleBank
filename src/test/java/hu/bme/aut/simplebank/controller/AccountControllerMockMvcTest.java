package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.LoginRequest;
import hu.bme.aut.simplebank.controller.dto.LoginResponse;
import hu.bme.aut.simplebank.controller.dto.account.CreateAccountRequest;
import hu.bme.aut.simplebank.controller.dto.account.UpdateAccountStatusRequest;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AccountControllerMockMvcTest {

    private static final String ADMIN_EMAIL = "admin-acc@bank.local";
    private static final String ADMIN_PASSWORD = "AdminPass123";
    private static final String CLIENT_EMAIL = "client-acc@bank.local";
    private static final String CLIENT_PASSWORD = "ClientPass123";
    private static final String OTHER_EMAIL = "other-acc@bank.local";
    private static final String OTHER_PASSWORD = "OtherPass123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private AppUser client;
    private AppUser other;
    private Account clientAccount;
    private Account otherAccount;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        persistUser("Acc Admin", ADMIN_EMAIL, ADMIN_PASSWORD, AppUser.Role.ADMIN);
        client = persistUser("Acc Client", CLIENT_EMAIL, CLIENT_PASSWORD, AppUser.Role.CLIENT);
        other = persistUser("Other Client", OTHER_EMAIL, OTHER_PASSWORD, AppUser.Role.CLIENT);

        clientAccount = persistAccount(client, "HU0000000000001");
        otherAccount = persistAccount(other, "HU0000000000002");
    }

    private AppUser persistUser(String name, String email, String password, AppUser.Role role) {
        AppUser u = new AppUser();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        return userRepository.save(u);
    }

    private Account persistAccount(AppUser owner, String number) {
        Account a = new Account();
        a.setAccountNumber(number);
        a.setBalance(BigDecimal.ZERO);
        a.setCurrency("HUF");
        a.setStatus(Account.Status.ACTIVE);
        a.setOwner(owner);
        return accountRepository.save(a);
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), LoginResponse.class);
        return "Bearer " + body.token();
    }


    @Test
    void clientCanCreateOwnAccount() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        CreateAccountRequest req = new CreateAccountRequest("EUR");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.ownerId").value(client.getId()))
                .andExpect(jsonPath("$.accountNumber").isString());
    }

    @Test
    void adminCanCreateAccountForThemselves() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        CreateAccountRequest req = new CreateAccountRequest("USD");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void createValidationFailsForBadCurrency() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        String invalidBody = "{\"currency\":\"eur\"}";

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void adminListsAllAccounts() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void clientListsOwnAccountsOnly() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ownerId").value(client.getId()));
    }


    @Test
    void clientCanGetOwnAccountById() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(get("/api/accounts/" + clientAccount.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientAccount.getId()));
    }

    @Test
    void clientCannotGetAnotherClientsAccount() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(get("/api/accounts/" + otherAccount.getId())
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanGetAnyAccount() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/accounts/" + otherAccount.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherAccount.getId()));
    }

    @Test
    void missingAccountReturns404() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/accounts/99999")
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }


    @Test
    void adminCanPatchStatus() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        UpdateAccountStatusRequest req = new UpdateAccountStatusRequest(Account.Status.LOCKED);

        mockMvc.perform(patch("/api/accounts/" + clientAccount.getId() + "/status")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    void clientCannotPatchStatus() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        UpdateAccountStatusRequest req = new UpdateAccountStatusRequest(Account.Status.LOCKED);

        mockMvc.perform(patch("/api/accounts/" + clientAccount.getId() + "/status")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }


    @Test
    void adminCanDeleteAccount() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(delete("/api/accounts/" + clientAccount.getId())
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void clientCannotDeleteAccount() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(delete("/api/accounts/" + clientAccount.getId())
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeletingMissingAccountReturns404() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(delete("/api/accounts/99999")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }


    @Test
    void unauthenticatedAccountAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }
}
