-- Historial de cambios de precio por servicio
-- Historia: SGL-053 ADM-SERV-PRICE

CREATE TABLE service_price_history (
    id              BIGSERIAL     PRIMARY KEY,
    service_id      BIGINT        NOT NULL REFERENCES services(id),
    precio_anterior NUMERIC(12,2) NOT NULL,
    precio_nuevo    NUMERIC(12,2) NOT NULL,
    fecha_cambio    TIMESTAMP     NOT NULL
);

CREATE INDEX idx_price_history_service_id ON service_price_history(service_id);
