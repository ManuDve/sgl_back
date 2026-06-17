package cl.sgl.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Utilitario para generar y validar JWT.
 * Emite dos tipos de token:
 *  - Admin (role=ADMIN, 8h): para el panel administrativo.
 *  - Gestión (type=GESTION, 10min): token de corta vida para que un cliente
 *    reagende o cancele su propia cita tras verificar su identidad con OTP.
 *
 * Historias: SGL-043 ADM-AUTH-JWT, SGL-067 GES-VERIFY
 */
@Component
public class JwtUtil {

    private static final long   MANAGEMENT_TOKEN_MILLIS = 10L * 60L * 1000L;
    private static final String TYPE_CLAIM   = "type";
    private static final String TYPE_GESTION = "GESTION";

    private final Key signingKey;
    private final long expirationMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-hours:8}") long expirationHours) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET es requerido para iniciar la aplicacion");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationHours * 60L * 60L * 1000L;
    }

    public String generateToken(String email, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
            .setSubject(email)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiration)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Object role = parseClaims(token).get("role");
        return role == null ? null : role.toString();
    }

    // ── Token de gestión (SGL-067 GES-VERIFY) ─────────────────────────────

    /**
     * Genera un JWT de corta vida (10 min) que autoriza gestionar una cita
     * específica. Incluye claim {@code type=GESTION} para distinguirlo del
     * token de administrador.
     */
    public String generateManagementToken(String idExterno) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(idExterno)
            .claim(TYPE_CLAIM, TYPE_GESTION)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + MANAGEMENT_TOKEN_MILLIS))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Valida que el token sea de gestión, esté vigente y corresponda al
     * idExterno indicado.
     */
    public boolean isManagementTokenValid(String token, String idExterno) {
        try {
            Claims claims = parseClaims(token);
            return TYPE_GESTION.equals(claims.get(TYPE_CLAIM))
                && idExterno.equals(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extrae el idExterno del token de gestión.
     *
     * @throws JwtException si el token no es de tipo GESTION
     */
    public String extractManagementIdExterno(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_GESTION.equals(claims.get(TYPE_CLAIM))) {
            throw new JwtException("El token no es de gestión");
        }
        return claims.getSubject();
    }
}

