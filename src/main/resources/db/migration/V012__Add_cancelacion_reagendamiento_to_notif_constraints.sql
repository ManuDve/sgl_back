-- SGL-073 GES-NOTIF: agrega CANCELACION_CLIENTE y REAGENDAMIENTO_CLIENTE a los constraints
-- de tipo en notification_log y email_retry_queue.

-- notification_log: reemplaza el constraint agregado en V010 con la lista completa
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS chk_notif_tipo;
ALTER TABLE notification_log ADD CONSTRAINT chk_notif_tipo
    CHECK (tipo IN (
        'CONFIRMACION_CLIENTE',
        'NOTIF_ADMIN',
        'REMINDER_24H',
        'REMINDER_2H',
        'OTP_VERIFICACION',
        'CANCELACION_CLIENTE',
        'REAGENDAMIENTO_CLIENTE'
    ));

-- email_retry_queue: reemplaza el constraint original de V006 con la lista completa
ALTER TABLE email_retry_queue DROP CONSTRAINT IF EXISTS chk_retry_tipo_email;
ALTER TABLE email_retry_queue ADD CONSTRAINT chk_retry_tipo_email
    CHECK (tipo_email IN (
        'CONFIRMACION_CLIENTE',
        'NOTIF_ADMIN',
        'REMINDER_24H',
        'REMINDER_2H',
        'CANCELACION_CLIENTE',
        'REAGENDAMIENTO_CLIENTE'
    ));
