package cl.sgl.config;

import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentStatus;
import cl.sgl.entity.LegalService;
import cl.sgl.repository.AppointmentRepository;
import cl.sgl.repository.LegalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Inserta datos de prueba al iniciar en perfil dev.
 * Solo ejecuta si la tabla appointments está vacía.
 *
 * Activar con: --spring.profiles.active=dev
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final AppointmentRepository appointmentRepository;
    private final LegalServiceRepository legalServiceRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (appointmentRepository.count() > 0) {
            log.info("[DataSeeder] Tabla appointments no está vacía — seed omitido");
            return;
        }

        log.info("[DataSeeder] Insertando datos de prueba…");

        LegalService divorcioCivil  = findOrCreate("Divorcio Civil",      "Tramitación de divorcio por mutuo acuerdo o contencioso",   new BigDecimal("85000"));
        LegalService laboral        = findOrCreate("Derecho Laboral",      "Asesoría en despidos, liquidaciones y contratos de trabajo", new BigDecimal("65000"));
        LegalService familia        = findOrCreate("Derecho de Familia",   "Pensión de alimentos, tuición y régimen de visitas",         new BigDecimal("75000"));
        LegalService tributario     = findOrCreate("Asesoría Tributaria",  "Declaración de renta, IVA y planificación tributaria",       new BigDecimal("120000"));

        LocalDate hoy = LocalDate.now();

        List<Appointment> appointments = List.of(
            build("AG-2026-0001", "Juan Pérez",       "juan.perez@gmail.com",       "+56912345678", divorcioCivil, hoy.plusDays(2),  LocalTime.of( 9,  0), 85000,  AppointmentStatus.PENDING),
            build("AG-2026-0002", "María González",   "maria.gonzalez@outlook.com", "+56923456789", laboral,       hoy.plusDays(3),  LocalTime.of(10,  0), 65000,  AppointmentStatus.PENDING),
            build("AG-2026-0003", "Carlos Rodríguez", "carlos.rod@gmail.com",       "+56934567890", familia,       hoy.plusDays(5),  LocalTime.of(11, 30), 75000,  AppointmentStatus.PENDING),
            build("AG-2026-0004", "Ana Martínez",     "ana.martinez@gmail.com",     "+56945678901", tributario,    hoy.plusDays(7),  LocalTime.of(14,  0), 120000, AppointmentStatus.PENDING),
            build("AG-2026-0005", "Pedro Soto",       "pedro.soto@hotmail.com",     "+56956789012", divorcioCivil, hoy.plusDays(10), LocalTime.of(15,  0), 90000,  AppointmentStatus.CONFIRMED),
            build("AG-2026-0006", "Lucía Fernández",  "lucia.fern@gmail.com",       "+56967890123", laboral,       hoy.plusDays(12), LocalTime.of(16, 30), 65000,  AppointmentStatus.CONFIRMED),
            build("AG-2026-0007", "Diego Muñoz",      "diego.munoz@gmail.com",      "+56978901234", familia,       hoy.plusDays(15), LocalTime.of( 9, 30), 75000,  AppointmentStatus.CONFIRMED),
            build("AG-2026-0008", "Valentina López",  "vale.lopez@outlook.com",     "+56989012345", tributario,    hoy.plusDays(18), LocalTime.of(10, 30), 150000, AppointmentStatus.CANCELLED),
            build("AG-2026-0009", "Andrés Torres",    "andres.torres@gmail.com",    "+56990123456", divorcioCivil, hoy.plusDays(22), LocalTime.of(11,  0), 50000,  AppointmentStatus.CANCELLED),
            build("AG-2026-0010", "Camila Vásquez",   "camila.vasquez@gmail.com",   "+56901234567", laboral,       hoy.plusDays(28), LocalTime.of(14, 30), 65000,  AppointmentStatus.PENDING, true)
        );

        appointmentRepository.saveAll(appointments);
        log.info("[DataSeeder] {} agendamientos de prueba insertados", appointments.size());
    }

    private LegalService findOrCreate(String name, String description, BigDecimal price) {
        return legalServiceRepository.findByName(name).orElseGet(() ->
            legalServiceRepository.save(
                LegalService.builder()
                    .name(name)
                    .description(description)
                    .price(price)
                    .active(true)
                    .build()
            )
        );
    }

    private Appointment build(
            String idExterno, String nombre, String email, String telefono,
            LegalService service, LocalDate fecha, LocalTime hora,
            int monto, AppointmentStatus estado) {
        return build(idExterno, nombre, email, telefono, service, fecha, hora, monto, estado, false);
    }

    private Appointment build(
            String idExterno, String nombre, String email, String telefono,
            LegalService service, LocalDate fecha, LocalTime hora,
            int monto, AppointmentStatus estado, boolean reagendado) {

        return Appointment.builder()
            .idExterno(idExterno)
            .nombreCliente(nombre)
            .email(email)
            .telefono(telefono)
            .service(service)
            .fecha(fecha)
            .hora(hora)
            .monto(new BigDecimal(monto))
            .estado(estado)
            .reagendado(reagendado)
            .build();
    }
}
