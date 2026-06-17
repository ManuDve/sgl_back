-- Amplía el CHECK constraint de canal para incluir WhatsApp (SGL-034 NOTIF-WA-01)
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS chk_notif_canal;
ALTER TABLE notification_log ADD CONSTRAINT chk_notif_canal
    CHECK (canal IN ('EMAIL', 'WHATSAPP'));
