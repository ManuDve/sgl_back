-- SGL-035 NOTIF-REMIND
-- Registro de recordatorios enviados para evitar duplicados.
-- La restricción UNIQUE en (appointment_id, tipo) garantiza un solo envío por tipo.

CREATE TABLE reminder_log (
    id               BIGSERIAL     PRIMARY KEY,
    appointment_id   BIGINT        NOT NULL,
    tipo             VARCHAR(20)   NOT NULL,
    fecha_envio      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reminder_tipo    CHECK (tipo IN ('REMIND_24H', 'REMIND_2H')),
    CONSTRAINT uq_reminder_appt_tipo UNIQUE (appointment_id, tipo)
);

CREATE INDEX idx_reminder_log_appointment_id ON reminder_log (appointment_id);
