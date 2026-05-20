package hu.bme.aut.simplebank.util;

import hu.bme.aut.simplebank.controller.dto.LoginRequest;
import hu.bme.aut.simplebank.controller.dto.LoginResponse;
import hu.bme.aut.simplebank.entity.Account;
import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.repository.AccountRepository;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class MockMvcTestSupport {

    private MockMvcTestSupport() {}

    public static MockMvc buildMockMvc(WebApplicationContext context) {
        return MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    public static AppUser persistUser(AppUserRepository repo,
                                      PasswordEncoder encoder,
                                      String name,
                                      String email,
                                      String password,
                                      AppUser.Role role) {
        AppUser u = new AppUser();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        return repo.save(u);
    }

    public static Account persistAccount(AccountRepository repo,
                                         AppUser owner,
                                         String accountNumber,
                                         BigDecimal balance,
                                         String currency,
                                         Account.Status status) {
        Account a = new Account();
        a.setAccountNumber(accountNumber);
        a.setBalance(balance);
        a.setCurrency(currency);
        a.setStatus(status);
        a.setOwner(owner);
        return repo.save(a);
    }

    public static Account persistAccount(AccountRepository repo, AppUser owner, String accountNumber) {
        return persistAccount(repo, owner, accountNumber, BigDecimal.ZERO, "HUF", Account.Status.ACTIVE);
    }

    public static String bearerToken(MockMvc mockMvc,
                                     ObjectMapper objectMapper,
                                     String email,
                                     String password) throws Exception {
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
}
