package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.LoginRequest;
import hu.bme.aut.simplebank.controller.dto.LoginResponse;
import hu.bme.aut.simplebank.controller.dto.transaction.DepositRequest;
import hu.bme.aut.simplebank.controller.dto.transaction.TransferRequest;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import hu.bme.aut.simplebank.repository.TransactionRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class TransactionControllerMockMvcTest {

    private static final String ADMIN_EMAIL = "admin-tx@bank.local";
    private static final String ADMIN_PASSWORD = "AdminPass123";
    private static final String CLIENT_A_EMAIL = "client-a-tx@bank.local";
    private static final String CLIENT_A_PASSWORD = "ClientAPass123";
    private static final String CLIENT_B_EMAIL = "client-b-tx@bank.local";
    private static final String CLIENT_B_PASSWORD = "ClientBPass123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private AppUser clientA;
    private AppUser clientB;
    private Account accountA;     // owned by clientA, EUR, balance 100
    private Account accountB;     // owned by clientB, EUR, balance 0

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        persistUser("Tx Admin", ADMIN_EMAIL, ADMIN_PASSWORD, AppUser.Role.ADMIN);
        clientA = persistUser("Client A", CLIENT_A_EMAIL, CLIENT_A_PASSWORD, AppUser.Role.CLIENT);
        clientB = persistUser("Client B", CLIENT_B_EMAIL, CLIENT_B_PASSWORD, AppUser.Role.CLIENT);

        accountA = persistAccount(clientA, "HU-A-001", new BigDecimal("100.0000"), "EUR", Account.Status.ACTIVE);
        accountB = persistAccount(clientB, "HU-B-001", new BigDecimal("0.0000"), "EUR", Account.Status.ACTIVE);
    }

    private AppUser persistUser(String name, String email, String password, AppUser.Role role) {
        AppUser u = new AppUser();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        return userRepository.save(u);
    }

    private Account persistAccount(AppUser owner, String number, BigDecimal balance, String currency, Account.Status status) {
        Account a = new Account();
        a.setAccountNumber(number);
        a.setBalance(balance);
        a.setCurrency(currency);
        a.setStatus(status);
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
    void clientCanTransferFromOwnAccount() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        long countBefore = transactionRepository.count();
        TransferRequest req = new TransferRequest(accountA.getId(), accountB.getId(),
                new BigDecimal("30.00"), "lunch");

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.sourceAccountId").value(accountA.getId()))
                .andExpect(jsonPath("$.targetAccountId").value(accountB.getId()))
                .andExpect(jsonPath("$.amount").value(30.00));

        Account refreshedA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account refreshedB = accountRepository.findById(accountB.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("70").compareTo(refreshedA.getBalance()));
        assertEquals(0, new BigDecimal("30").compareTo(refreshedB.getBalance()));
        assertEquals(countBefore + 1, transactionRepository.count());
    }

    @Test
    void clientCannotTransferFromAnotherClientsAccount() throws Exception {
        String token = login(CLIENT_B_EMAIL, CLIENT_B_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), accountB.getId(),
                new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void insufficientFundsReturns400() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), accountB.getId(),
                new BigDecimal("99999.00"), null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void lockedTargetReturns400() throws Exception {
        accountB.setStatus(Account.Status.LOCKED);
        accountRepository.save(accountB);

        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), accountB.getId(),
                new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_STATE"));
    }

    @Test
    void currencyMismatchReturns400() throws Exception {
        Account usdAccount = persistAccount(clientB, "HU-B-USD", new BigDecimal("0.0000"), "USD", Account.Status.ACTIVE);

        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), usdAccount.getId(),
                new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void selfTransferReturns400() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), accountA.getId(),
                new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), 999999L,
                new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void validationFailureReturns422() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        String invalidBody = "{\"sourceAccountId\":null,\"targetAccountId\":null,\"amount\":-1}";

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void clientCanDepositToOwnAccount() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        DepositRequest req = new DepositRequest(accountA.getId(), new BigDecimal("50.00"), "cash");

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("CREDIT"))
                .andExpect(jsonPath("$.sourceAccountId").value(accountA.getId()))
                .andExpect(jsonPath("$.targetAccountId").value(accountA.getId()));

        Account refreshed = accountRepository.findById(accountA.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150").compareTo(refreshed.getBalance()));
    }

    @Test
    void clientCannotDepositToAnothersAccount() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        DepositRequest req = new DepositRequest(accountB.getId(), new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanDepositToAnyAccount() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        DepositRequest req = new DepositRequest(accountB.getId(), new BigDecimal("25.00"), "manual adj");

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("CREDIT"));
    }


    @Test
    void clientListsOwnAccountTransactions() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);
        TransferRequest req = new TransferRequest(accountA.getId(), accountB.getId(),
                new BigDecimal("5.00"), "test");
        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions/account/" + accountA.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceAccountId").value(accountA.getId()));
    }

    @Test
    void clientCannotListAnothersAccountTransactions() throws Exception {
        String token = login(CLIENT_A_EMAIL, CLIENT_A_PASSWORD);

        mockMvc.perform(get("/api/transactions/account/" + accountB.getId())
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        TransferRequest req = new TransferRequest(accountA.getId(), accountB.getId(),
                new BigDecimal("10.00"), null);
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
