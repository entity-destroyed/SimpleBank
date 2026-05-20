package hu.bme.aut.simplebank.controller;

import hu.bme.aut.simplebank.controller.dto.card.CreateCardRequest;
import hu.bme.aut.simplebank.controller.dto.card.UpdateCardLimitRequest;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.entity.BankCard;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import hu.bme.aut.simplebank.repository.BankCardRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class BankCardControllerMockMvcTest {

    private static final String ADMIN_EMAIL = "admin-card@bank.local";
    private static final String ADMIN_PASSWORD = "AdminPass123";
    private static final String CLIENT_EMAIL = "client-card@bank.local";
    private static final String CLIENT_PASSWORD = "ClientPass123";
    private static final String OTHER_EMAIL = "other-card@bank.local";
    private static final String OTHER_PASSWORD = "OtherPass123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankCardRepository cardRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private Account clientAccount;
    private Account otherAccount;
    private BankCard clientCard;
    private BankCard otherCard;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcTestSupport.buildMockMvc(context);

        MockMvcTestSupport.persistUser(userRepository, passwordEncoder,
                "Card Admin", ADMIN_EMAIL, ADMIN_PASSWORD, AppUser.Role.ADMIN);
        AppUser client = MockMvcTestSupport.persistUser(userRepository, passwordEncoder,
                "Card Client", CLIENT_EMAIL, CLIENT_PASSWORD, AppUser.Role.CLIENT);
        AppUser other = MockMvcTestSupport.persistUser(userRepository, passwordEncoder,
                "Other Client", OTHER_EMAIL, OTHER_PASSWORD, AppUser.Role.CLIENT);

        clientAccount = MockMvcTestSupport.persistAccount(accountRepository, client, "HU1000000000001");
        otherAccount = MockMvcTestSupport.persistAccount(accountRepository, other, "HU1000000000002");

        clientCard = persistCard(clientAccount, "1111222233334444");
        otherCard = persistCard(otherAccount, "5555666677778888");
    }

    private BankCard persistCard(Account account, String number) {
        BankCard c = new BankCard();
        c.setCardNumber(number);
        c.setExpirationDate(LocalDate.now().plusYears(3));
        c.setDailyLimit(new BigDecimal("1000.00"));
        c.setAccount(account);
        return cardRepository.save(c);
    }

    private String login(String email, String password) throws Exception {
        return MockMvcTestSupport.bearerToken(mockMvc, objectMapper, email, password);
    }


    @Test
    void adminCanCreateCardForGivenAccount() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        CreateCardRequest req = new CreateCardRequest(clientAccount.getId(), new BigDecimal("500.00"));

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(clientAccount.getId()))
                .andExpect(jsonPath("$.dailyLimit").value(500.00))
                .andExpect(jsonPath("$.cardNumber").isString())
                .andExpect(jsonPath("$.expirationDate").isString());
    }

    @Test
    void clientCannotCreateCard() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        CreateCardRequest req = new CreateCardRequest(clientAccount.getId(), new BigDecimal("500.00"));

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void createCardValidationFailsWithoutAccountId() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String invalidBody = "{\"accountId\":null,\"dailyLimit\":-1}";

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createCardForMissingAccountReturns404() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        CreateCardRequest req = new CreateCardRequest(99999L, new BigDecimal("100.00"));

        mockMvc.perform(post("/api/cards")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }


    @Test
    void clientListsOnlyOwnCards() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(get("/api/cards")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(clientCard.getId()));
    }

    @Test
    void adminListsAllCards() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/cards")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }


    @Test
    void clientCanUpdateOwnCardLimit() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        UpdateCardLimitRequest req = new UpdateCardLimitRequest(new BigDecimal("2500.00"));

        mockMvc.perform(put("/api/cards/" + clientCard.getId() + "/limit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(2500.00));
    }

    @Test
    void clientCannotUpdateAnotherClientsCardLimit() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        UpdateCardLimitRequest req = new UpdateCardLimitRequest(new BigDecimal("2500.00"));

        mockMvc.perform(put("/api/cards/" + otherCard.getId() + "/limit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void updateLimitValidationFailsForNegativeAmount() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);
        String invalidBody = "{\"dailyLimit\":-100}";

        mockMvc.perform(put("/api/cards/" + clientCard.getId() + "/limit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }


    @Test
    void clientCanDeleteOwnCard() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(delete("/api/cards/" + clientCard.getId())
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void clientCannotDeleteAnotherClientsCard() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(delete("/api/cards/" + otherCard.getId())
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingMissingCardReturns404() throws Exception {
        String token = login(CLIENT_EMAIL, CLIENT_PASSWORD);

        mockMvc.perform(delete("/api/cards/99999")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }


    @Test
    void unauthenticatedCardAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/cards"))
                .andExpect(status().isUnauthorized());
    }
}
