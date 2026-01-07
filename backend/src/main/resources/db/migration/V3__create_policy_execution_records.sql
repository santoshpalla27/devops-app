-- V3: Create policy_execution_records table for audit trail
-- Append-only table, no versioning needed

CREATE TABLE policy_execution_records (
    id VARCHAR(36) PRIMARY KEY,
    policy_id VARCHAR(36) NOT NULL,
    policy_name VARCHAR(255) NOT NULL,
    system_type VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    success BOOLEAN NOT NULL,
    message TEXT,
    executed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    duration_ms BIGINT NOT NULL DEFAULT 0,
    
    INDEX idx_exec_policy_id (policy_id),
    INDEX idx_exec_system_type (system_type),
    INDEX idx_exec_executed_at (executed_at),
    INDEX idx_exec_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
