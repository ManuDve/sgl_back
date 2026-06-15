-- SGL-040 NOTIF-AUDIT: historial de todas las notificaciones enviadas por email
CREATE TABLE notification_log (
    id              BIGSERIAL     PRIMARY KEY,
    appointment_id  BIGINT        NOT NULL,
    tipo            VARCHAR(30)   NOT NULL,
    canal           VARCHAR(20)   NOT NULL DEFAULT 'EMAIL',
    destinatario    VARCHAR(255)  NOT NULL,
    estado          VARCHAR(20)   NOT NULL,
    fecha_envio     TIMESTAMP     NOT NULL,
    error           TEXT,
    CONSTRAINT chk_notif_tipo   CHECK (tipo   IN ('CONFIRMACION_CLIENTE','NOTIF_ADMIN','REMINDER_24H','REMINDER_2H')),
    CONSTRAINT chk_notif_estado CHECK (estado IN ('ENVIADO','FALLIDO')),
    CONSTRAINT chk_notif_canal  CHECK (canal  IN ('EMAIL'))
);

CREATE INDEX idx_notif_log_appointment_id ON notification_log (appointment_id);
CREATE INDEX idx_notif_log_fecha_envio    ON notification_log (fecha_envio);
