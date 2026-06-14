package cl.sgl.repository;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de AppointmentSpecification.
 *
 * Ejecutan los lambdas de cada Specification directamente llamando a toPredicate(),
 * verificando que los métodos correctos de CriteriaBuilder sean invocados.
 *
 * Historia: SGL-050 ADM-FILTER
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentSpecification Tests")
@SuppressWarnings({"unchecked", "rawtypes"})
class AppointmentSpecificationTest {

    @Mock Root<Appointment> root;
    @Mock CriteriaQuery<?>  query;
    @Mock CriteriaBuilder   cb;
    @Mock Path              mockPath;
    @Mock Expression        mockExpr;
    @Mock Predicate         mockPredicate;

    @BeforeEach
    void setUp() {
        // cb.equal se stubbea por test con doReturn para evitar ambigüedad entre
        // equal(Expression, Object) y equal(Expression, Expression).
        lenient().when(root.get(anyString())).thenReturn(mockPath);
        lenient().when(cb.lower(any(Expression.class))).thenReturn(mockExpr);
        lenient().when(cb.like(any(Expression.class), anyString())).thenReturn(mockPredicate);
        lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(mockPredicate);
        lenient().when(cb.lessThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(mockPredicate);
        lenient().when(cb.or(any(Predicate[].class))).thenReturn(mockPredicate);
    }

    // ── hasEstado ─────────────────────────────────────────────────────

    @Test
    @DisplayName("hasEstado delega en cb.equal sobre el campo 'estado'")
    void testHasEstado_LlamaCbEqual() {
        // Argumento exacto fuerza la resolución al overload equal(Expression, Object)
        // en lugar del equal(Expression, Expression), evitando ambigüedad de Mockito.
        doReturn(mockPredicate).when(cb).equal(mockPath, AppointmentStatus.PENDING);

        Specification<Appointment> spec = AppointmentSpecification.hasEstado(AppointmentStatus.PENDING);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(mockPredicate, result);
        verify(root).get("estado");
        verify(cb).equal(mockPath, AppointmentStatus.PENDING);
    }

    // ── searchText ────────────────────────────────────────────────────

    @Test
    @DisplayName("searchText aplica LIKE en nombreCliente, email e idExterno con patrón %texto%")
    void testSearchText_LlamaCbOrConTresLike() {
        Specification<Appointment> spec = AppointmentSpecification.searchText("juan");

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(mockPredicate, result);
        verify(root).get("nombreCliente");
        verify(root).get("email");
        verify(root).get("idExterno");
        verify(cb, times(3)).lower(mockPath);
        verify(cb, times(3)).like(mockExpr, "%juan%");
        verify(cb).or(any(Predicate[].class));
    }

    @Test
    @DisplayName("searchText convierte el texto a minúsculas para LIKE case-insensitive")
    void testSearchText_ConvierteAMinusculas() {
        Specification<Appointment> spec = AppointmentSpecification.searchText("MAYUSCULAS");

        spec.toPredicate(root, query, cb);

        verify(cb, times(3)).like(mockExpr, "%mayusculas%");
    }

    // ── fechaDesde ────────────────────────────────────────────────────

    @Test
    @DisplayName("fechaDesde delega en cb.greaterThanOrEqualTo sobre el campo 'fecha'")
    void testFechaDesde_LlamaCbGreaterThanOrEqualTo() {
        LocalDate desde = LocalDate.of(2026, 6, 1);
        Specification<Appointment> spec = AppointmentSpecification.fechaDesde(desde);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(mockPredicate, result);
        verify(root).get("fecha");
        verify(cb).greaterThanOrEqualTo(mockPath, desde);
    }

    // ── fechaHasta ────────────────────────────────────────────────────

    @Test
    @DisplayName("fechaHasta delega en cb.lessThanOrEqualTo sobre el campo 'fecha'")
    void testFechaHasta_LlamaCbLessThanOrEqualTo() {
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        Specification<Appointment> spec = AppointmentSpecification.fechaHasta(hasta);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(mockPredicate, result);
        verify(root).get("fecha");
        verify(cb).lessThanOrEqualTo(mockPath, hasta);
    }
}
