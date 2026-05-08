package cl.sgl.service;

import cl.sgl.config.JwtUtil;
import cl.sgl.dto.LoginRequest;
import cl.sgl.dto.LoginResponse;
import cl.sgl.entity.AdminUser;
import cl.sgl.exception.UnauthorizedException;
import cl.sgl.repository.AdminUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para AuthService.
 * Historia: SGL-041 ADM-LOGIN
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Login exitoso genera JWT")
    void loginSuccessGeneratesToken() {
        LoginRequest request = LoginRequest.builder()
            .email("admin@sgl.cl")
            .password("admin123")
            .build();

        AdminUser adminUser = AdminUser.builder()
            .id(1L)
            .email("admin@sgl.cl")
            .password("hashed")
            .role("ADMIN")
            .build();

        when(adminUserRepository.findByEmail("admin@sgl.cl")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("admin123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("admin@sgl.cl", "ADMIN")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("admin@sgl.cl", response.getEmail());
        verify(adminUserRepository).findByEmail("admin@sgl.cl");
        verify(passwordEncoder).matches("admin123", "hashed");
        verify(jwtUtil).generateToken("admin@sgl.cl", "ADMIN");
    }

    @Test
    @DisplayName("Login falla con credenciales invalidas")
    void loginFailsWithInvalidCredentials() {
        LoginRequest request = LoginRequest.builder()
            .email("admin@sgl.cl")
            .password("bad")
            .build();

        AdminUser adminUser = AdminUser.builder()
            .email("admin@sgl.cl")
            .password("hashed")
            .role("ADMIN")
            .build();

        when(adminUserRepository.findByEmail("admin@sgl.cl")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }
}

