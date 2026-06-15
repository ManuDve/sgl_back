package cl.sgl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de emails fallidos pendientes de reintento.
 * El scheduler RetryScheduler los procesa cada 15 minutos con backoff exponencial.
 *
 * Historia: SGL-038 NOTIF-RETRY
 */
@Entity
@Table(name = "email_retry_queue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRetryQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_email", nullable = false, length = 30)
    private TipoEmail tipoEmail;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "proximo_intento", nullable = false)
    private LocalDateTime proximoIntento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoRetry estado;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
