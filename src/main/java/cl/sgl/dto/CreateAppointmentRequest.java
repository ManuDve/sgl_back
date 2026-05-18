package cl.sgl.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de entrada para crear un nuevo agendamiento público.
 *
 * Historia: SGL-024 AG-IDEXTERNO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAppointmentRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombreCliente;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    private String email;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "\\+56\\s*9(\\s*\\d){8}", message = "El teléfono debe comenzar con +569 seguido de 8 dígitos (ej: +56912345678)")
    private String telefono;

    @NotNull(message = "El servicio es obligatorio")
    private Long serviceId;

    @NotNull(message = "La fecha es obligatoria")
    @Future(message = "La fecha debe ser posterior a hoy")
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;

    @NotNull(message = "Debes aceptar los términos y condiciones")
    @AssertTrue(message = "Debes aceptar los términos y condiciones")
    private Boolean aceptaTerminos;
}
