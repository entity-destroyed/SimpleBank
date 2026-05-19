package hu.bme.aut.simplebank.config;

import hu.bme.aut.simplebank.entity.AppUser;
import hu.bme.aut.simplebank.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.bootstrap.email}") String adminEmail,
            @Value("${admin.bootstrap.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(AppUser.Role.ADMIN)) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setName("Bootstrap Admin");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(AppUser.Role.ADMIN);
        userRepository.save(admin);
        log.info("Bootstrap admin created: {}", adminEmail);
    }
}
