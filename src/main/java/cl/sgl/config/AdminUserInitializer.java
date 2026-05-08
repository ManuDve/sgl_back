package cl.sgl.config;

import cl.sgl.entity.AdminUser;
import cl.sgl.repository.AdminUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Inicializa un admin de prueba si no existe.
 * Historia: SGL-043 ADM-AUTH-JWT
 */
@Component
@Profile("!test")
public class AdminUserInitializer implements CommandLineRunner {

    private static final String DEFAULT_EMAIL = "admin@sgl.cl";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserInitializer(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminUserRepository.existsByEmail(DEFAULT_EMAIL)) {
            return;
        }

        AdminUser adminUser = AdminUser.builder()
            .email(DEFAULT_EMAIL)
            .password(passwordEncoder.encode(DEFAULT_PASSWORD))
            .role("ADMIN")
            .build();

        adminUserRepository.save(adminUser);
    }
}

