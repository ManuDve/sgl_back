package cl.sgl.repository;

import cl.sgl.entity.AppointmentOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Historia: SGL-066 GES-OTP
 */
public interface AppointmentOtpRepository extends JpaRepository<AppointmentOtp, Long> {

    /** Busca un OTP válido: código correcto, no usado y no expirado. */
    Optional<AppointmentOtp> findByAppointmentIdAndOtpAndUsadoFalseAndExpiresAtAfter(
        Long appointmentId, String otp, LocalDateTime now);

    /** Invalida todos los OTPs pendientes de un agendamiento (antes de generar uno nuevo). */
    @Modifying
    @Query("UPDATE AppointmentOtp o SET o.usado = true WHERE o.appointmentId = :appointmentId AND o.usado = false")
    void invalidatePendingByAppointmentId(@Param("appointmentId") Long appointmentId);
}
