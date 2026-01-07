-- V5: Create event outbox table for reliable Kafka event delivery
-- Implements the transactional outbox pattern

CREATE TABLE event_outbox (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    system_type VARCHAR(50) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        COMMENT 'PENDING, PROCESSING, DELIVERED, DLQ',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP(6) NULL COMMENT 'Next scheduled dispatch attempt',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    delivered_at TIMESTAMP(6) NULL,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    
    UNIQUE INDEX idx_outbox_event_id (event_id),
    INDEX idx_outbox_status_retry (status, next_retry_at),
    INDEX idx_outbox_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
