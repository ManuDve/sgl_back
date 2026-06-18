package cl.sgl.service;

import cl.sgl.dto.OtpRequest;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentOtp;
import cl.sgl.exception.OtpCooldownException;
import cl.sgl.repository.AppointmentOtpRepository;
import cl.sgl.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Servicio de OTP para verificación de identidad en reagendamiento/cancelación.
 *
 * Flujo de requestOtp:
 *  1. Busca la cita por idExterno — si no existe, retorna silenciosamente (evita enumeración)
 *  2. Verifica que el email o teléfono proporcionado coincide con la cita
 *  3. Si coincide, genera un OTP de 6 dígitos con 15 minutos de vida y lo envía por email
 *
 * El endpoint siempre retorna 200 independientemente del resultado (patrón de respuesta uniforme).
 *
 * Historia: SGL-066 GES-OTP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    static final int OTP_EXPIRY_MINUTES  = 15;
    static final int OTP_COOLDOWN_SECONDS = 60;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppointmentOtpRepository otpRepository;
    private final AppointmentRepository    appointmentRepository;
    private final EmailService             emailService;

    /**
     * Genera un OTP de 6 dígitos para el agendamiento indicado.
     * Invalida cualquier OTP pendiente anterior del mismo agendamiento.
     *
     * @param appointmentId ID interno del agendamiento
     * @return código OTP generado
     */
    @Transactional
    public String generateOtp(Long appointmentId) {
        otpRepository.invalidatePendingByAppointmentId(appointmentId);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        AppointmentOtp entity = AppointmentOtp.builder()
            .appointmentId(appointmentId)
            .otp(code)
            .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
            .usado(false)
            .createdAt(LocalDateTime.now())
            .build();

        otpRepository.save(entity);
        log.info("OTP generado para appointment {}", appointmentId);
        return code;
    }

    /**
     * Valida un OTP: verifica que sea correcto, no esté usado y no haya expirado.
     * Si es válido, lo marca como usado.
     *
     * @param appointmentId ID interno del agendamiento
     * @param otp           código OTP a validar
     * @return true si el OTP es válido y fue consumido, false en caso contrario
     */
    @Transactional
    public boolean validateOtp(Long appointmentId, String otp) {
        Optional<AppointmentOtp> found = otpRepository
            .findByAppointmentIdAndOtpAndUsadoFalseAndExpiresAtAfter(
                appointmentId, otp, LocalDateTime.now());

        if (found.isEmpty()) {
            log.warn("OTP inválido o expirado para appointment {}", appointmentId);
            return false;
        }

        AppointmentOtp entity = found.get();
        entity.setUsado(true);
        otpRepository.save(entity);
        log.info("OTP validado para appointment {}", appointmentId);
        return true;
    }

    /**
     * Solicitud de OTP: verifica identidad y envía el código por email si coincide.
     * Retorna siempre sin revelar si la cita existe o si los datos coinciden.
     *
     * @param idExterno identificador externo de la cita
     * @param request   email o teléfono del cliente para verificar identidad
     */
    @Transactional
    public void requestOtp(String idExterno, OtpRequest request) {
        Optional<Appointment> optAppt = appointmentRepository.findByIdExterno(idExterno);
        if (optAppt.isEmpty()) {
            log.warn("OTP solicitado para idExterno inexistente: {}", idExterno);
            return;
        }

        Appointment appointment = optAppt.get();
        if (!matchesIdentity(appointment, request)) {
            log.warn("OTP solicitado con datos que no coinciden para: {}", idExterno);
            return;
        }

        // Cooldown: rechaza si el último OTP se generó hace menos de 60 segundos
        otpRepository.findFirstByAppointmentIdOrderByCreatedAtDesc(appointment.getId())
            .ifPresent(ultimo -> {
                long segundos = Duration.between(ultimo.getCreatedAt(), LocalDateTime.now()).getSeconds();
                if (segundos < OTP_COOLDOWN_SECONDS) {
                    long restantes = OTP_COOLDOWN_SECONDS - segundos;
                    log.warn("Cooldown activo para {} — faltan {} segundos", idExterno, restantes);
                    throw new OtpCooldownException(restantes);
                }
            });

        String code = generateOtp(appointment.getId());
        try {
            emailService.sendOtpEmail(appointment, code);
        } catch (Exception e) {
            log.error("Error enviando OTP por email para {} — {}", idExterno, e.getMessage());
        }
    }

    /**
     * Busca la cita por idExterno y valida el OTP.
     * Retorna false silenciosamente si la cita no existe (evita enumeración).
     *
     * @param idExterno identificador externo de la cita
     * @param otp       código OTP ingresado por el cliente
     * @return true si el OTP es válido y fue consumido
     */
    @Transactional
    public boolean verifyOtp(String idExterno, String otp) {
        Optional<Appointment> optAppt = appointmentRepository.findByIdExterno(idExterno);
        if (optAppt.isEmpty()) {
            log.warn("verify-otp para idExterno inexistente: {}", idExterno);
            return false;
        }
        return validateOtp(optAppt.get().getId(), otp);
    }

    /**
     * Verifica si el email o teléfono del request coincide con el agendamiento.
     * La comparación de teléfono usa solo dígitos para tolerar distintos formatos.
     */
    boolean matchesIdentity(Appointment appointment, OtpRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return appointment.getEmail().equalsIgnoreCase(request.getEmail().trim());
        }
        if (request.getTelefono() != null && !request.getTelefono().isBlank()) {
            return normalizePhone(appointment.getTelefono())
                .equals(normalizePhone(request.getTelefono()));
        }
        return false;
    }

    /** Extrae los últimos 9 dígitos del número para comparación independiente del prefijo. */
    private static String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }
}
