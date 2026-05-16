package cl.sgl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Respuesta paginada por semana para la vista de calendario del panel admin.
 * Historia: SGL-049 ADM-CAL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentCalendarDTO {

    /** Mes consultado en formato YYYY-MM */
    private String mes;

    /** Número de semana dentro del mes (1-indexado) */
    private int semana;

    /** Total de semanas del mes — ceil(diasDelMes / 7) */
    private int totalSemanas;

    /** Primer día de la semana consultada (inclusive), formato YYYY-MM-DD */
    private String desde;

    /** Último día de la semana consultada (inclusive), formato YYYY-MM-DD */
    private String hasta;

    /** true si es la primera semana del mes */
    private boolean primera;

    /** true si es la última semana del mes */
    private boolean ultima;

    /**
     * Agendamientos agrupados por fecha.
     * Clave: "YYYY-MM-DD" — solo incluye fechas con al menos un agendamiento.
     * Valor: lista de AppointmentSummaryDTO ordenados por hora ASC.
     */
    private Map<String, List<AppointmentSummaryDTO>> dias;
}
