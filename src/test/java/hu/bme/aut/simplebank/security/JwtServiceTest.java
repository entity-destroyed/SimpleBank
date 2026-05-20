package hu.bme.aut.simplebank.security;

import hu.bme.aut.simplebank.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "MTIzNDU2NzgxMjM0NTY3ODEyMzQ1Njc4MTIzNDU2NzgxMjM0NTY3ODEyMzQ1Njc4MTIzNDU2NzgxMjM0NTY3OA==";

    private JwtService jwtService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60_000L);
        user = new AppUser();
        user.setId(42L);
        user.setEmail("alice@example.com");
        user.setRole(AppUser.Role.CLIENT);
    }

    @Test
    void roundTripPreservesSubjectAndValidates() {
        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertTrue(jwtService.isValid(token));
        assertEquals("alice@example.com", jwtService.extractEmail(token));
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService shortLived = new JwtService(SECRET, 1L);
        String token = shortLived.generateToken(user);
        Thread.sleep(50);
        assertFalse(shortLived.isValid(token));
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertFalse(jwtService.isValid(tampered));
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String otherSecret =
                "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=";
        JwtService attacker = new JwtService(otherSecret, 60_000L);
        String token = attacker.generateToken(user);
        assertFalse(jwtService.isValid(token));
    }
}
