package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.EmailRetryQueue;
import cl.sgl.entity.EstadoRetry;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.EmailRetryQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler que reintenta enviar emails fallidos registrados en email_retry_queue.
 *
 * Frecuencia: cada 15 minutos (configurable con retry.cron).
 * Backoff exponencial: próximo intento = ahora + 15min × 2^intentos.
 * Máximo 3 intentos; si se supera, la entrada pasa a FALLIDO.
 *
 * Historia: SGL-038 NOTIF-RETRY
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private static final int    MAX_ATTEMPTS       = 3;
    private static final int    BASE_INTERVAL_MIN  = 15;
    private static final int    MAX_ERROR_LENGTH   = 500;
    private static final ZoneId ZONE_CL            = ZoneId.of("America/Santiago");

    private final EmailRetryQueueRepository retryQueueRepository;
    private final AppointmentRepository     appointmentRepository;
    private final EmailService              emailService;

    @Scheduled(cron = "${retry.cron:0 */15 * * * *}", zone = "America/Santiago")
    public void processRetries() {
        LocalDateTime now = LocalDateTime.now(ZONE_CL);
        log.info("RetryScheduler ejecutado — buscando emails pendientes...");

        List<EmailRetryQueue> pending = retryQueueRepository
            .findByEstadoAndProximoIntentoLessThanEqualOrderByProximoIntento(EstadoRetry.PENDIENTE, now);

        if (pending.isEmpty()) {
            log.debug("No hay emails pendientes de reintento");
            return;
        }

        log.info("Emails pendientes de reintento: {}", pending.size());
        for (EmailRetryQueue entry : pending) {
            processEntry(entry, now);
        }
    }

    /**
     * Procesa una entrada de la cola: reintenta el envío y actualiza el estado.
     * Si el agendamiento ya no existe, marca la entrada como FALLIDO.
     */
    void processEntry(EmailRetryQueue entry, LocalDateTime now) {
        Optional<Appointment> optAppt = appointmentRepository.findById(entry.getAppointmentId());
        if (optAppt.isEmpty()) {
            log.warn("Agendamiento ID={} no encontrado — marcando entrada {} como FALLIDO",
                entry.getAppointmentId(), entry.getId());
            entry.setEstado(EstadoRetry.FALLIDO);
            entry.setUltimoError("Agendamiento no encontrado");
            retryQueueRepository.save(entry);
            return;
        }

        Appointment appointment = optAppt.get();
        entry.setIntentos(entry.getIntentos() + 1);

        try {
            emailService.retryEmail(entry, appointment);
            entry.setEstado(EstadoRetry.ENVIADO);
            log.info("Reintento exitoso — tipo={} appt={} intentos={}",
                entry.getTipoEmail(), entry.getAppointmentId(), entry.getIntentos());
        } catch (Exception e) {
            log.warn("Reintento fallido — tipo={} appt={} intentos={} — {}",
                entry.getTipoEmail(), entry.getAppointmentId(), entry.getIntentos(), e.getMessage());

            String truncatedError = (e.getMessage() != null)
                ? e.getMessage().substring(0, Math.min(e.getMessage().length(), MAX_ERROR_LENGTH))
                : null;
            entry.setUltimoError(truncatedError);

            if (entry.getIntentos() >= MAX_ATTEMPTS) {
                entry.setEstado(EstadoRetry.FALLIDO);
                log.error("Email marcado como FALLIDO — tipo={} appt={} — superó {} intentos",
                    entry.getTipoEmail(), entry.getAppointmentId(), MAX_ATTEMPTS);
            } else {
                // Backoff exponencial: 15min × 2^intentos → 30min, 60min
                long delayMinutes = (long) BASE_INTERVAL_MIN * (1L << entry.getIntentos());
                entry.setProximoIntento(now.plusMinutes(delayMinutes));
            }
        }

        retryQueueRepository.save(entry);
    }
}
