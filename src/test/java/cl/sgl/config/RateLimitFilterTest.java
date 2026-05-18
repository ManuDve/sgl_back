package cl.sgl.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para RateLimitFilter.
 * Historia: SGL-097 SEC-RATELIMIT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest {

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private HttpServletRequest  request;
    private HttpServletResponse response;
    private FilterChain         chain;
    private PrintWriter         writer;

    @BeforeEach
    void setUp() throws Exception {
        request  = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain    = mock(FilterChain.class);
        writer   = mock(PrintWriter.class);

        // lenient: no todos los tests llegan al código de IP o de 429
        lenient().when(response.getWriter()).thenReturn(writer);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    @Test
    @DisplayName("Ruta no aplicable pasa el filtro sin consumir tokens")
    void testPathNoAplicable_PasaLibremente() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");

        rateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Primera solicitud a /api/appointments pasa el filtro")
    void testPrimeraSolicitud_Pasa() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/appointments");

        rateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Primera solicitud a /api/services pasa el filtro")
    void testServicesPath_Pasa() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/services");

        rateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("La solicitud 21 retorna 429 con mensaje y timestamp")
    void testExcedeLimite_Retorna429ConTimestamp() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/appointments");

        for (int i = 0; i < 20; i++) {
            rateLimitFilter.doFilter(request, response, chain);
        }

        rateLimitFilter.doFilter(request, response, chain);

        verify(chain, times(20)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(writer).write(argThat((String body) ->
            body.contains("\"status\":429") &&
            body.contains("Demasiadas solicitudes") &&
            body.contains("timestamp")
        ));
    }

    @Test
    @DisplayName("IPs distintas tienen cubos independientes")
    void testIpsDistintas_CubosIndependientes() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/appointments");

        HttpServletRequest request2 = mock(HttpServletRequest.class);
        when(request2.getRequestURI()).thenReturn("/api/appointments");
        when(request2.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request2.getHeader("X-Forwarded-For")).thenReturn(null);

        // Agota el cubo de 127.0.0.1
        for (int i = 0; i < 20; i++) {
            rateLimitFilter.doFilter(request, response, chain);
        }
        rateLimitFilter.doFilter(request, response, chain); // solicitud 21 → bloqueada

        // 10.0.0.2 tiene cubo propio y pasa
        rateLimitFilter.doFilter(request2, response, chain);

        verify(chain, times(21)).doFilter(any(), any()); // 20 de IP1 + 1 de IP2
        verify(response, times(1)).setStatus(429);       // solo IP1 fue bloqueada
    }

    @Test
    @DisplayName("X-Forwarded-For se usa como IP real (primer valor de la cadena)")
    void testXForwardedFor_UsaIpReal() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/appointments");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

        rateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }
}
