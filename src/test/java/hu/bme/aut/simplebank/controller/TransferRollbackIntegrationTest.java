package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.LoginRequest;
import hu.bme.aut.simplebank.controller.dto.LoginResponse;
import hu.bme.aut.simplebank.controller.dto.transaction.TransferRequest;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.entity.Transaction;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import hu.bme.aut.simplebank.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TransferRollbackIntegrationTest {

    private static final String EMAIL = "rollback-client@bank.local";
    private static final String PASSWORD = "RollbackPass123";

    @MockitoBean
    private TransactionRepository transactionRepository;

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

    private Long userId;
    private Long sourceId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new RuntimeException("simulated persistence failure"));

        AppUser u = new AppUser();
        u.setName("Rollback Client");
        u.setEmail(EMAIL);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(AppUser.Role.CLIENT);
        userId = userRepository.save(u).getId();

        Account source = new Account();
        source.setAccountNumber("ROLL-SRC-" + System.nanoTime());
        source.setBalance(new BigDecimal("100.0000"));
        source.setCurrency("EUR");
        source.setStatus(Account.Status.ACTIVE);
        source.setOwner(u);
        sourceId = accountRepository.save(source).getId();

        Account target = new Account();
        target.setAccountNumber("ROLL-TGT-" + System.nanoTime());
        target.setBalance(new BigDecimal("0.0000"));
        target.setCurrency("EUR");
        target.setStatus(Account.Status.ACTIVE);
        target.setOwner(u);
        targetId = accountRepository.save(target).getId();
    }

    @AfterEach
    void cleanUp() {
        if (sourceId != null) accountRepository.deleteById(sourceId);
        if (targetId != null) accountRepository.deleteById(targetId);
        if (userId != null) userRepository.deleteById(userId);
    }

    @Test
    void transferRollsBackBothBalancesWhenTransactionPersistFails() throws Exception {
        LoginRequest loginReq = new LoginRequest(EMAIL, PASSWORD);
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readValue(loginBody, LoginResponse.class).token();

        TransferRequest req = new TransferRequest(sourceId, targetId, new BigDecimal("30.00"), "boom");

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError());

        Account refreshedSource = accountRepository.findById(sourceId).orElseThrow();
        Account refreshedTarget = accountRepository.findById(targetId).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(refreshedSource.getBalance()),
                "source balance must be unchanged after rollback");
        assertEquals(0, new BigDecimal("0").compareTo(refreshedTarget.getBalance()),
                "target balance must be unchanged after rollback");
    }
}
