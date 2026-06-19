package cl.sgl.service;

import cl.sgl.dto.AuditLogDTO;
import cl.sgl.dto.AuditPageResponse;
import cl.sgl.entity.AuditLog;
import cl.sgl.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para AuditService.
 * Historia: SGL-055 ADM-AUDIT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── log con email explícito ───────────────────────────────────────────────

    @Test
    @DisplayName("testLogConEmailExplicito_GuardaRegistroConEmailIndicado")
    void testLogConEmailExplicito_GuardaRegistroConEmailIndicado() {
        auditService.log("LOGIN", "AUTH", null, "admin@sgl.cl", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("LOGIN",         saved.getAccion());
        assertEquals("AUTH",          saved.getEntidad());
        assertNull(saved.getEntidadId());
        assertEquals("admin@sgl.cl",  saved.getAdminEmail());
        assertNull(saved.getDetalles());
        assertNotNull(saved.getFechaAccion());
    }

    @Test
    @DisplayName("testLogConEmailExplicito_ConEntidadIdYDetalles_GuardaTodosCampos")
    void testLogConEmailExplicito_ConEntidadIdYDetalles_GuardaTodosCampos() {
        auditService.log("CAMBIO_ESTADO", "AGENDAMIENTO", "AG-2026-0001", "admin@sgl.cl", "PENDING → CANCELLED");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("CAMBIO_ESTADO",    saved.getAccion());
        assertEquals("AGENDAMIENTO",     saved.getEntidad());
        assertEquals("AG-2026-0001",     saved.getEntidadId());
        assertEquals("PENDING → CANCELLED", saved.getDetalles());
    }

    @Test
    @DisplayName("testLogConEmailExplicito_ExcepcionEnRepo_NoLanzaException")
    void testLogConEmailExplicito_ExcepcionEnRepo_NoLanzaException() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        // No debe propagar la excepción — audit never fails the caller
        auditService.log("LOGIN", "AUTH", null, "admin@sgl.cl", null);
    }

    // ── log con SecurityContext ───────────────────────────────────────────────

    @Test
    @DisplayName("testLogDesdeSecurityContext_ConAutenticacion_UsaEmailDelPrincipal")
    void testLogDesdeSecurityContext_ConAutenticacion_UsaEmailDelPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin@sgl.cl", null, List.of())
        );

        auditService.log("CONFIRMAR_PAGO", "AGENDAMIENTO", "AG-2026-0001", "codigo=TX123 monto=50000");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertEquals("admin@sgl.cl", captor.getValue().getAdminEmail());
    }

    @Test
    @DisplayName("testLogDesdeSecurityContext_SinAutenticacion_UsaSistema")
    void testLogDesdeSecurityContext_SinAutenticacion_UsaSistema() {
        auditService.log("CAMBIO_PRECIO", "SERVICIO", "1", "100000 → 120000");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertEquals("sistema", captor.getValue().getAdminEmail());
    }

    // ── getPage ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("testGetPage_RetornaPageMapeadaADTO")
    void testGetPage_RetornaPageMapeadaADTO() {
        AuditLog entry = AuditLog.builder()
            .id(1L)
            .accion("LOGIN")
            .entidad("AUTH")
            .entidadId(null)
            .adminEmail("admin@sgl.cl")
            .detalles(null)
            .fechaAccion(LocalDateTime.of(2026, 6, 18, 10, 0))
            .build();

        Page<AuditLog> repoPage = new PageImpl<>(List.of(entry));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(repoPage);

        AuditPageResponse result = auditService.getPage(0, 20);

        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());

        AuditLogDTO dto = result.content().get(0);
        assertEquals(1L,             dto.id());
        assertEquals("LOGIN",        dto.accion());
        assertEquals("AUTH",         dto.entidad());
        assertNull(dto.entidadId());
        assertEquals("admin@sgl.cl", dto.adminEmail());
        assertNull(dto.detalles());
        assertEquals(LocalDateTime.of(2026, 6, 18, 10, 0), dto.fechaAccion());
    }

    @Test
    @DisplayName("testGetPage_PaginaVacia_RetornaPageVacia")
    void testGetPage_PaginaVacia_RetornaPageVacia() {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        AuditPageResponse result = auditService.getPage(0, 20);

        assertNotNull(result);
        assertEquals(0, result.totalElements());
        assertEquals(0, result.content().size());
    }
}
