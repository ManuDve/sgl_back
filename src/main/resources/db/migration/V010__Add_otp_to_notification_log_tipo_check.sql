-- Agrega OTP_VERIFICACION al check constraint de tipo en notification_log (SGL-066 GES-OTP)
-- Nota: el constraint puede tener nombre auto-generado (notification_log_tipo_check) si fue
-- aplicado manualmente sin el prefijo CONSTRAINT de V007.
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS chk_notif_tipo;
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_tipo_check;
ALTER TABLE notification_log ADD CONSTRAINT chk_notif_tipo
    CHECK (tipo IN ('CONFIRMACION_CLIENTE','NOTIF_ADMIN','REMINDER_24H','REMINDER_2H','OTP_VERIFICACION'));
