package cl.sgl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entidad que representa un agendamiento de consulta legal.
 *
 * Historias: SGL-045 ADM-LIST-PEND, SGL-016 AG-NOLOGIN, SGL-100 LEGAL-CONSENT
 */
@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appointment_estado", columnList = "estado"),
    @Index(name = "idx_appointment_fecha", columnList = "fecha"),
    @Index(name = "idx_appointment_id_externo", columnList = "id_externo", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_externo", nullable = false, unique = true, length = 20)
    private String idExterno;

    @Column(name = "nombre_cliente", nullable = false, length = 255)
    private String nombreCliente;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private LegalService service;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora", nullable = false)
    private LocalTime hora;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus estado = AppointmentStatus.PENDING;

    @Column(name = "acepta_terminos", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    @Builder.Default
    private Boolean aceptaTerminos = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
