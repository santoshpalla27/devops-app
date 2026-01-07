-- V1: Create chaos_experiments table for persistent chaos experiment storage
-- Supports recovery on restart by tracking status and scheduled end times

CREATE TABLE chaos_experiments (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    system_type VARCHAR(50) NOT NULL,
    fault_type VARCHAR(50) NOT NULL,
    duration_seconds BIGINT NOT NULL DEFAULT 60,
    latency_ms BIGINT NULL,
    failure_rate_percent INT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    ended_at TIMESTAMP(6) NULL,
    scheduled_end_at TIMESTAMP(6) NULL COMMENT 'Used for recovery scheduling',
    result TEXT,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    
    INDEX idx_chaos_status (status),
    INDEX idx_chaos_system_type (system_type),
    INDEX idx_chaos_scheduled_end (scheduled_end_at),
    INDEX idx_chaos_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
