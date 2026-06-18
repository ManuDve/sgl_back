package cl.sgl.entity;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public static AppointmentStatus fromString(String value) {
        return switch (value.toUpperCase().trim()) {
            case "PENDING",   "PENDIENTE"  -> PENDING;
            case "CONFIRMED", "CONFIRMADO" -> CONFIRMED;
            case "CANCELLED", "CANCELADO"  -> CANCELLED;
            default -> throw new IllegalArgumentException(
                "Estado inválido: '" + value + "'. Valores válidos: PENDING/PENDIENTE, CONFIRMED/CONFIRMADO, CANCELLED/CANCELADO"
            );
        };
    }
}
