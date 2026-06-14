package cl.sgl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro de recordatorios de cita enviados por correo.
 * La restricción única (appointment_id, tipo) impide envíos duplicados.
 *
 * Historia: SGL-035 NOTIF-REMIND
 */
@Entity
@Table(name = "reminder_log", uniqueConstraints = {
    @UniqueConstraint(name = "uq_reminder_appt_tipo", columnNames = {"appointment_id", "tipo"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private ReminderTipo tipo;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;
}
