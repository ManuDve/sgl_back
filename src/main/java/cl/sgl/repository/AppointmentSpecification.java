package cl.sgl.repository;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Predicados JPA reutilizables para filtrado dinámico de agendamientos.
 * Se componen con Specification.where(...).and(...) en AppointmentService.
 *
 * Historia: SGL-050 ADM-FILTER
 */
public class AppointmentSpecification {

    private AppointmentSpecification() {}

    public static Specification<Appointment> hasEstado(AppointmentStatus estado) {
        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    /** Busca en nombreCliente, email e idExterno (case-insensitive). */
    public static Specification<Appointment> searchText(String text) {
        return (root, query, cb) -> {
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("nombreCliente")), pattern),
                cb.like(cb.lower(root.get("email")),         pattern),
                cb.like(cb.lower(root.get("idExterno")),     pattern)
            );
        };
    }

    public static Specification<Appointment> fechaDesde(LocalDate desde) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde);
    }

    public static Specification<Appointment> fechaHasta(LocalDate hasta) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), hasta);
    }
}
