-- Control plane database initialization

CREATE DATABASE IF NOT EXISTS controlplane;
USE controlplane;

-- System events table (for testing)
CREATE TABLE IF NOT EXISTS system_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    system_name VARCHAR(50) NOT NULL,
    message TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_system_name (system_name),
    INDEX idx_created_at (created_at)
);

-- Create replication user
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'replpassword';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
