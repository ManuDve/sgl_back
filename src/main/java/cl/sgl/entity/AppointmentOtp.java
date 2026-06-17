package cl.sgl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OTP de un solo uso para verificar identidad antes de reagendar o cancelar una cita.
 * Historia: SGL-066 GES-OTP
 */
@Entity
@Table(name = "appointment_otp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(nullable = false, length = 6)
    private String otp;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean usado;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
