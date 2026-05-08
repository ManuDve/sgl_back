-- Migración para crear tabla de servicios
-- Historia: SGL-052 ADM-SERV-CRUD
-- Fecha: 2026-05-08

CREATE TABLE IF NOT EXISTS services (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_service_name UNIQUE (name)
);

-- Índices
CREATE INDEX idx_service_active ON services (active);
CREATE INDEX idx_service_created_at ON services (created_at);

-- Datos de ejemplo (comentado para producción)
-- INSERT INTO services (name, description, price, active, created_at, updated_at) VALUES
-- ('Divorcio Contencioso', 'Trámite de divorcio con contestación', 500000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- ('Herencias', 'Trámite de herencias y sucesiones', 350000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- ('Cobro de Deuda', 'Demanda por cobro de deuda', 250000, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
