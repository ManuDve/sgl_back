package cl.sgl.entity;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    RESCHEDULED;

    public static AppointmentStatus fromString(String value) {
        try {
            return AppointmentStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Estado inválido: '" + value + "'. Valores válidos: PENDING, CONFIRMED, CANCELLED, RESCHEDULED"
            );
        }
    }
}
