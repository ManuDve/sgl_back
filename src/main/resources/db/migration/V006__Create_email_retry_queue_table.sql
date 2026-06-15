-- SGL-038 NOTIF-RETRY
-- Cola de reintento para emails fallidos. Backoff exponencial, máximo 3 intentos.
-- Estado: PENDIENTE → ENVIADO | FALLIDO

CREATE TABLE email_retry_queue (
    id               BIGSERIAL     PRIMARY KEY,
    appointment_id   BIGINT        NOT NULL,
    tipo_email       VARCHAR(30)   NOT NULL,
    intentos         INT           NOT NULL DEFAULT 0,
    proximo_intento  TIMESTAMP     NOT NULL,
    estado           VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    ultimo_error     TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_retry_tipo_email CHECK (tipo_email IN ('CONFIRMACION_CLIENTE', 'NOTIF_ADMIN', 'REMINDER_24H', 'REMINDER_2H')),
    CONSTRAINT chk_retry_estado     CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO'))
);

CREATE INDEX idx_retry_queue_estado_proximo ON email_retry_queue (estado, proximo_intento);
CREATE INDEX idx_retry_queue_appointment_id ON email_retry_queue (appointment_id);
