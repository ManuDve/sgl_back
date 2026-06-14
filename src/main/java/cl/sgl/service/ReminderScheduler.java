package cl.sgl.service;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.ReminderLog;
import cl.sgl.entity.ReminderTipo;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.ReminderLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Scheduler que envía recordatorios de cita a clientes confirmados.
 *
 * El momento de envío se controla con variables de entorno:
 *   REMINDER_FIRST_LEAD_MINUTES  — minutos antes de la cita para el primer recordatorio (default: 1440 = 24h)
 *   REMINDER_SECOND_LEAD_MINUTES — minutos antes de la cita para el segundo recordatorio (default: 120 = 2h)
 *   REMINDER_INTERVAL_MINUTES    — frecuencia interna del scheduler (default: 60 min)
 *
 * La tabla reminder_log evita envíos duplicados via restricción UNIQUE (appointment_id, tipo).
 *
 * Historia: SGL-035 NOTIF-REMIND
 */
@Component
@Slf4j
public class ReminderScheduler {

    private static final ZoneId ZONA_CL = ZoneId.of("America/Santiago");

    private final AppointmentRepository appointmentRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final EmailService          emailService;
    private final int                   firstLeadMinutes;
    private final int                   secondLeadMinutes;

    public ReminderScheduler(
            AppointmentRepository appointmentRepository,
            ReminderLogRepository reminderLogRepository,
            EmailService emailService,
            @Value("${reminder.first-lead-minutes:1440}")  int firstLeadMinutes,
            @Value("${reminder.second-lead-minutes:120}")  int secondLeadMinutes) {
        this.appointmentRepository = appointmentRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.emailService          = emailService;
        this.firstLeadMinutes      = firstLeadMinutes;
        this.secondLeadMinutes     = secondLeadMinutes;
    }

    @Scheduled(cron = "${reminder.cron:0 0 * * * *}", zone = "America/Santiago")
    public void procesarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now(ZONA_CL);
        log.info("Scheduler ejecutado — buscando recordatorios desde {}", ahora);
        enviarRecordatorios(ahora.plusMinutes(firstLeadMinutes),  ReminderTipo.REMIND_24H);
        enviarRecordatorios(ahora.plusMinutes(secondLeadMinutes), ReminderTipo.REMIND_2H);
    }

    /**
     * Busca citas CONFIRMADAS para la fecha y hora indicadas por {@code objetivo}
     * y envía un recordatorio del tipo dado si aún no se ha enviado.
     *
     * La hora se redondea a la hora en punto para coincidir con los slots de la agenda.
     */
    void enviarRecordatorios(LocalDateTime objetivo, ReminderTipo tipo) {
        LocalDate fecha = objetivo.toLocalDate();
        LocalTime hora  = objetivo.toLocalTime().withMinute(0).withSecond(0).withNano(0);

        log.info("Buscando {} — fecha: {}, hora: {}", tipo, fecha, hora);

        List<Appointment> citas = appointmentRepository.findByEstadoAndFechaAndHora(
            AppointmentStatus.CONFIRMED, fecha, hora);

        log.info("Citas encontradas para {} {}/{}: {}", tipo, fecha, hora, citas.size());

        for (Appointment cita : citas) {
            if (reminderLogRepository.existsByAppointmentIdAndTipo(cita.getId(), tipo)) {
                continue;
            }
            boolean enviado = emailService.sendReminderEmail(cita, tipo);
            if (enviado) {
                reminderLogRepository.save(ReminderLog.builder()
                    .appointmentId(cita.getId())
                    .tipo(tipo)
                    .fechaEnvio(LocalDateTime.now(ZONA_CL))
                    .build());
                log.info("Recordatorio {} registrado para {}", tipo, cita.getIdExterno());
            }
        }
    }
}
