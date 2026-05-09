-- Migración para crear tabla de agendamientos
-- Historias: SGL-045 ADM-LIST-PEND, SGL-016 AG-NOLOGIN, SGL-100 LEGAL-CONSENT
-- Fecha: 2026-05-08
-- Nota: referencia documental — el esquema es gestionado por Hibernate ddl-auto=update

CREATE TABLE IF NOT EXISTS appointments (
    id          SERIAL PRIMARY KEY,
    id_externo  VARCHAR(20)     NOT NULL UNIQUE,
    nombre_cliente VARCHAR(255) NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    telefono    VARCHAR(20),
    service_id  BIGINT          NOT NULL REFERENCES services(id),
    fecha       DATE            NOT NULL,
    hora        TIME            NOT NULL,
    monto       NUMERIC(12, 2)  NOT NULL,
    estado          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    acepta_terminos BOOLEAN         NOT NULL DEFAULT FALSE,  -- SGL-016 AG-NOLOGIN / SGL-100 LEGAL-CONSENT
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_estado CHECK (estado IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'RESCHEDULED'))
);

CREATE INDEX idx_appointment_estado     ON appointments (estado);
CREATE INDEX idx_appointment_fecha      ON appointments (fecha);
