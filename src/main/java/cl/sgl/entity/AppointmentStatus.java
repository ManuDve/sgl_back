package cl.sgl.entity;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    /**
     * Valor heredado — solo para compatibilidad con filas de DB anteriores a V011.
     * La migración V011 convierte estas filas a PENDING+reagendado=true.
     * No exponer via API ni usar en código nuevo.
     */
    RESCHEDULED;

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
