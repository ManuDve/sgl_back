-- SGL-055 ADM-AUDIT: registro de acciones administrativas para trazabilidad
CREATE TABLE audit_log (
    id           BIGSERIAL     PRIMARY KEY,
    accion       VARCHAR(50)   NOT NULL,
    entidad      VARCHAR(50)   NOT NULL,
    entidad_id   VARCHAR(50),
    admin_email  VARCHAR(255)  NOT NULL,
    detalles     TEXT,
    fecha_accion TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_accion CHECK (accion IN (
        'LOGIN', 'CAMBIO_ESTADO', 'CONFIRMAR_PAGO', 'CAMBIO_PRECIO'
    ))
);

CREATE INDEX idx_audit_log_admin_email  ON audit_log (admin_email);
CREATE INDEX idx_audit_log_fecha_accion ON audit_log (fecha_accion DESC);
CREATE INDEX idx_audit_log_entidad      ON audit_log (entidad, entidad_id);
