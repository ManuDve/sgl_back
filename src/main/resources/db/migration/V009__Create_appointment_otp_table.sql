-- SGL-066 GES-OTP
-- OTPs de un solo uso para verificar identidad antes de reagendar o cancelar una cita.

CREATE TABLE appointment_otp (
    id              BIGSERIAL PRIMARY KEY,
    appointment_id  BIGINT       NOT NULL REFERENCES appointments(id),
    otp             VARCHAR(6)   NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    usado           BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_appt_otp_appointment_id ON appointment_otp(appointment_id);
