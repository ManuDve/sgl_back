package cl.sgl.config;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para JwtUtil — token admin y token de gestión.
 * Historias: SGL-043 ADM-AUTH-JWT, SGL-067 GES-VERIFY
 */
@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-must-be-32bytes!!";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 8L);
    }

    // ── generateToken / admin ──────────────────────────────────────────────

    @Test
    @DisplayName("generateToken produce token válido con email y rol")
    void testGenerateToken_ProduceTokenValido() {
        String token = jwtUtil.generateToken("admin@sgl.cl", "ADMIN");

        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals("admin@sgl.cl", jwtUtil.extractEmail(token));
        assertEquals("ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    @DisplayName("isTokenValid retorna false para token manipulado")
    void testIsTokenValid_TokenManipulado_RetornaFalse() {
        assertFalse(jwtUtil.isTokenValid("token.invalido.firmado"));
    }

    // ── generateManagementToken ────────────────────────────────────────────

    @Test
    @DisplayName("generateManagementToken incluye idExterno como subject y type=GESTION")
    void testGenerateManagementToken_ContieneIdExternoYTipoGestion() {
        String token = jwtUtil.generateManagementToken("AG-ABCD-0001");

        assertEquals("AG-ABCD-0001", jwtUtil.extractManagementIdExterno(token));
        assertTrue(jwtUtil.isManagementTokenValid(token, "AG-ABCD-0001"));
    }

    @Test
    @DisplayName("generateManagementToken produce token que expira en ~10 min")
    void testGenerateManagementToken_ExpiraEn10Minutos() {
        long antes = System.currentTimeMillis();
        String token = jwtUtil.generateManagementToken("AG-ABCD-0001");
        long despues = System.currentTimeMillis();

        Date expiracion = jwtUtil.parseClaims(token).getExpiration();
        long deltaMs = expiracion.getTime() - antes;

        // Entre 9:59 y 10:01 minutos
        assertTrue(deltaMs > 9 * 60 * 1000L, "El token debe expirar en más de 9 minutos");
        assertTrue(deltaMs < 11 * 60 * 1000L + (despues - antes), "El token debe expirar en menos de 11 minutos");
    }

    // ── isManagementTokenValid ─────────────────────────────────────────────

    @Test
    @DisplayName("isManagementTokenValid retorna false si idExterno no coincide")
    void testIsManagementTokenValid_IdExternoDistinto_RetornaFalse() {
        String token = jwtUtil.generateManagementToken("AG-ABCD-0001");

        assertFalse(jwtUtil.isManagementTokenValid(token, "AG-ABCD-0002"));
    }

    @Test
    @DisplayName("isManagementTokenValid retorna false para un token de admin")
    void testIsManagementTokenValid_TokenAdmin_RetornaFalse() {
        String adminToken = jwtUtil.generateToken("admin@sgl.cl", "ADMIN");

        assertFalse(jwtUtil.isManagementTokenValid(adminToken, "admin@sgl.cl"));
    }

    @Test
    @DisplayName("isManagementTokenValid retorna false para token inválido")
    void testIsManagementTokenValid_TokenInvalido_RetornaFalse() {
        assertFalse(jwtUtil.isManagementTokenValid("basura.token.aqui", "AG-ABCD-0001"));
    }

    // ── extractManagementIdExterno ─────────────────────────────────────────

    @Test
    @DisplayName("extractManagementIdExterno retorna el idExterno del token de gestión")
    void testExtractManagementIdExterno_TokenValido_RetornaIdExterno() {
        String token = jwtUtil.generateManagementToken("AG-ZJPT-0078");

        assertEquals("AG-ZJPT-0078", jwtUtil.extractManagementIdExterno(token));
    }

    @Test
    @DisplayName("extractManagementIdExterno lanza JwtException para token de admin")
    void testExtractManagementIdExterno_TokenAdmin_LanzaJwtException() {
        String adminToken = jwtUtil.generateToken("admin@sgl.cl", "ADMIN");

        assertThrows(JwtException.class,
            () -> jwtUtil.extractManagementIdExterno(adminToken));
    }
}
