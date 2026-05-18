package cl.sgl.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro de rate limiting para endpoints públicos.
 * Aplica a /api/appointments y /api/services: máximo 20 requests/minuto por IP.
 * Usa Bucket4j con token bucket en memoria (ConcurrentHashMap por IP).
 *
 * Nota: el mapa crece con cada IP nueva. Para escala alta migrar a Bucket4j con Redis.
 *
 * Historia: SGL-097 SEC-RATELIMIT
 */
@Component
@Order(1)
@Slf4j
public class RateLimitFilter implements Filter {

    private static final int    CAPACITY      = 20;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/appointments") && !path.startsWith("/api/services")) {
            chain.doFilter(request, response);
            return;
        }

        String ip     = resolveClientIp(httpRequest);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit excedido — IP: {}, path: {}", ip, path);
            sendTooManyRequests((HttpServletResponse) response);
        }
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.intervally(CAPACITY, REFILL_PERIOD));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Extrae la IP real considerando proxies inversos (X-Forwarded-For). */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"status\":429,\"message\":\"Demasiadas solicitudes, intenta más tarde\",\"timestamp\":\"%s\"}",
            LocalDateTime.now()
        ));
    }
}
