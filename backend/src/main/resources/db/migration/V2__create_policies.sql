-- V2: Create policies table for persistent policy storage
-- Condition is stored as JSON for flexibility with different condition types

CREATE TABLE policies (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    system_type VARCHAR(50) NOT NULL,
    condition_type VARCHAR(50) NOT NULL COMMENT 'StateCondition, LatencyCondition, AndCondition',
    condition_json JSON NOT NULL COMMENT 'Serialized PolicyCondition',
    action VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown_seconds BIGINT NOT NULL DEFAULT 60,
    description TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    
    UNIQUE INDEX idx_policy_name (name),
    INDEX idx_policy_system_type (system_type),
    INDEX idx_policy_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
