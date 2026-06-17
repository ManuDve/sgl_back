package cl.sgl.service;

import cl.sgl.dto.OtpRequest;
import cl.sgl.entity.Appointment;
import cl.sgl.entity.AppointmentOtp;
import cl.sgl.entity.LegalService;
import cl.sgl.repository.AppointmentOtpRepository;
import cl.sgl.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para OtpService.
 * Historia: SGL-066 GES-OTP
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService Tests")
class OtpServiceTest {

    @Mock private AppointmentOtpRepository otpRepository;
    @Mock private AppointmentRepository    appointmentRepository;
    @Mock private EmailService             emailService;

    @InjectMocks
    private OtpService otpService;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        LegalService servicio = LegalService.builder()
            .id(1L).name("Divorcio").price(new BigDecimal("300000")).active(true)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        appointment = Appointment.builder()
            .id(10L)
            .idExterno("AG-TEST-0010")
            .nombreCliente("Ana Martínez")
            .email("ana@example.com")
            .telefono("+56912345678")
            .service(servicio)
            .fecha(LocalDate.of(2026, 7, 1))
            .hora(LocalTime.of(10, 0))
            .monto(new BigDecimal("300000"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ── generateOtp ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generateOtp invalida OTPs pendientes antes de crear uno nuevo")
    void testGenerateOtp_InvalidaPendientesAnteriores() {
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.generateOtp(10L);

        verify(otpRepository).invalidatePendingByAppointmentId(10L);
    }

    @Test
    @DisplayName("generateOtp guarda OTP con 6 dígitos y expiración de 15 minutos")
    void testGenerateOtp_GuardaOtpConExpiracion() {
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String code = otpService.generateOtp(10L);

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"), "El OTP debe ser numérico de 6 dígitos");

        ArgumentCaptor<AppointmentOtp> captor = ArgumentCaptor.forClass(AppointmentOtp.class);
        verify(otpRepository).save(captor.capture());
        AppointmentOtp saved = captor.getValue();

        assertEquals(10L, saved.getAppointmentId());
        assertEquals(code, saved.getOtp());
        assertFalse(saved.isUsado());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(saved.getExpiresAt().isBefore(
            LocalDateTime.now().plusMinutes(OtpService.OTP_EXPIRY_MINUTES + 1)));
    }

    @Test
    @DisplayName("generateOtp retorna el código guardado")
    void testGenerateOtp_RetornaCodigo() {
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String code = otpService.generateOtp(10L);

        assertNotNull(code);
        assertFalse(code.isBlank());
    }

    // ── validateOtp ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validateOtp con código válido retorna true y marca como usado")
    void testValidateOtp_OtpValidoRetornaTrueYMarcaUsado() {
        AppointmentOtp otp = AppointmentOtp.builder()
            .id(1L).appointmentId(10L).otp("123456")
            .expiresAt(LocalDateTime.now().plusMinutes(10)).usado(false)
            .createdAt(LocalDateTime.now()).build();
        when(otpRepository.findByAppointmentIdAndOtpAndUsadoFalseAndExpiresAtAfter(
            eq(10L), eq("123456"), any(LocalDateTime.class))).thenReturn(Optional.of(otp));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = otpService.validateOtp(10L, "123456");

        assertTrue(result);
        assertTrue(otp.isUsado());
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("validateOtp con código incorrecto retorna false")
    void testValidateOtp_OtpIncorrectoRetornaFalse() {
        when(otpRepository.findByAppointmentIdAndOtpAndUsadoFalseAndExpiresAtAfter(
            anyLong(), anyString(), any())).thenReturn(Optional.empty());

        boolean result = otpService.validateOtp(10L, "000000");

        assertFalse(result);
        verify(otpRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateOtp con OTP ya usado retorna false (no encuentra por used=false)")
    void testValidateOtp_OtpYaUsadoRetornaFalse() {
        when(otpRepository.findByAppointmentIdAndOtpAndUsadoFalseAndExpiresAtAfter(
            anyLong(), anyString(), any())).thenReturn(Optional.empty());

        boolean result = otpService.validateOtp(10L, "123456");

        assertFalse(result);
    }

    // ── requestOtp ────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestOtp con idExterno inexistente retorna sin enviar nada")
    void testRequestOtp_IdExternoInexistente_RetornaSilencioso() {
        when(appointmentRepository.findByIdExterno("AG-XXXX-9999")).thenReturn(Optional.empty());

        otpService.requestOtp("AG-XXXX-9999", new OtpRequest("test@test.com", null));

        verify(otpRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("requestOtp con email que no coincide retorna sin enviar")
    void testRequestOtp_EmailNoCoincide_RetornaSilencioso() {
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(appointment));

        otpService.requestOtp("AG-TEST-0010", new OtpRequest("otro@email.com", null));

        verify(otpRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("requestOtp con email correcto genera OTP y envía email")
    void testRequestOtp_EmailCorrecto_GeneraOtpYEnviaEmail() {
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(appointment));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.requestOtp("AG-TEST-0010", new OtpRequest("ana@example.com", null));

        verify(otpRepository).save(any(AppointmentOtp.class));
        verify(emailService).sendOtpEmail(eq(appointment), anyString());
    }

    @Test
    @DisplayName("requestOtp con email en distinto caso coincide (case-insensitive)")
    void testRequestOtp_EmailDistintoCaso_Coincide() {
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(appointment));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.requestOtp("AG-TEST-0010", new OtpRequest("ANA@EXAMPLE.COM", null));

        verify(emailService).sendOtpEmail(eq(appointment), anyString());
    }

    @Test
    @DisplayName("requestOtp con teléfono correcto (distintos formatos) genera OTP y envía")
    void testRequestOtp_TelefonoCorrecto_DistintoFormato_Coincide() {
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(appointment));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // appointment tiene "+56912345678", el cliente envía sin prefijo
        otpService.requestOtp("AG-TEST-0010", new OtpRequest(null, "912345678"));

        verify(emailService).sendOtpEmail(eq(appointment), anyString());
    }

    @Test
    @DisplayName("requestOtp con teléfono incorrecto retorna sin enviar")
    void testRequestOtp_TelefonoNoCoincide_RetornaSilencioso() {
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(appointment));

        otpService.requestOtp("AG-TEST-0010", new OtpRequest(null, "999999999"));

        verify(emailService, never()).sendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("requestOtp si sendOtpEmail falla no lanza excepción")
    void testRequestOtp_FallaEmail_NoLanzaExcepcion() {
        when(appointmentRepository.findByIdExterno("AG-TEST-0010")).thenReturn(Optional.of(appointment));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SMTP error")).when(emailService).sendOtpEmail(any(), any());

        assertDoesNotThrow(() ->
            otpService.requestOtp("AG-TEST-0010", new OtpRequest("ana@example.com", null)));
    }

    // ── matchesIdentity ───────────────────────────────────────────────────

    @Test
    @DisplayName("matchesIdentity retorna false si ni email ni teléfono son provistos")
    void testMatchesIdentity_SinEmailNiTelefono_RetornaFalse() {
        assertFalse(otpService.matchesIdentity(appointment, new OtpRequest(null, null)));
        assertFalse(otpService.matchesIdentity(appointment, new OtpRequest("", "  ")));
    }
}
