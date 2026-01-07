-- V4: Create desired_system_states table for reconciliation targets
-- Uses system_type as natural primary key

CREATE TABLE desired_system_states (
    system_type VARCHAR(50) PRIMARY KEY,
    desired_state VARCHAR(50) NOT NULL,
    max_latency_ms INT NOT NULL DEFAULT 1000,
    max_retry_count INT NOT NULL DEFAULT 3,
    auto_recover BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default desired states for known systems
INSERT INTO desired_system_states (system_type, desired_state, max_latency_ms, max_retry_count, auto_recover, created_at, updated_at)
VALUES 
    ('mysql', 'CONNECTED', 1000, 3, TRUE, NOW(6), NOW(6)),
    ('redis', 'CONNECTED', 500, 3, TRUE, NOW(6), NOW(6)),
    ('kafka', 'CONNECTED', 2000, 5, TRUE, NOW(6), NOW(6));
