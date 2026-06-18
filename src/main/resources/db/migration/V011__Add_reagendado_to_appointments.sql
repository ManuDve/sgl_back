-- Agrega bandera reagendado para registrar si una cita fue movida de fecha/hora.
-- El estado (PENDING/CONFIRMED) se mantiene intacto al reagendar.
ALTER TABLE appointments ADD COLUMN reagendado BOOLEAN NOT NULL DEFAULT FALSE;

-- Filas existentes con estado RESCHEDULED vuelven a su estado base PENDING
-- y quedan marcadas como reagendadas.
UPDATE appointments SET estado = 'PENDING', reagendado = TRUE WHERE estado = 'RESCHEDULED';
