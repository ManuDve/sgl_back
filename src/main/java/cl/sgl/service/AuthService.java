package cl.sgl.service;

import cl.sgl.config.JwtUtil;
import cl.sgl.dto.LoginRequest;
import cl.sgl.dto.LoginResponse;
import cl.sgl.entity.AdminUser;
import cl.sgl.exception.UnauthorizedException;
import cl.sgl.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cl.sgl.service.AuditService.*;

/**
 * Servicio de autenticacion para administradores.
 * Historia: SGL-041 ADM-LOGIN
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        log.info("Intento de login admin: {}", email);

        AdminUser adminUser = adminUserRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Credenciales invalidas"));

        if (!passwordEncoder.matches(request.getPassword(), adminUser.getPassword())) {
            throw new UnauthorizedException("Credenciales invalidas");
        }

        String token = jwtUtil.generateToken(adminUser.getEmail(), adminUser.getRole());
        auditService.log(ACCION_LOGIN, ENTIDAD_AUTH, null, email, null);
        return LoginResponse.builder()
            .token(token)
            .email(adminUser.getEmail())
            .build();
    }
}

