CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_pendentes ON outbox_events (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id);

COMMENT ON TABLE outbox_events IS 'Eventos gravados na mesma transacao do agregado e publicados depois no RabbitMQ';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Id do video que originou o evento';
COMMENT ON COLUMN outbox_events.event_type IS 'Routing key do evento no exchange fiapx.video';
COMMENT ON COLUMN outbox_events.payload IS 'Corpo JSON do evento, ja no formato do contrato';
COMMENT ON COLUMN outbox_events.published_at IS 'Nulo enquanto o broker nao confirmou a publicacao';
COMMENT ON INDEX idx_outbox_pendentes IS 'Indice parcial que o dispatcher varre a cada ciclo';
