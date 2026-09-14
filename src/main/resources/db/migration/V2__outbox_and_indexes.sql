-- =====================================================================
-- V2: Transactional outbox
-- =====================================================================

CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,


    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,

    event_type     VARCHAR(80)  NOT NULL,
    payload        JSONB        NOT NULL,

    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts       SMALLINT     NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_pending
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
