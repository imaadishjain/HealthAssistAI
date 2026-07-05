-- HealthAssist AI Database Schema
-- Tables for normal application data and embedded/vector data tracking.
-- NOTE: Run init-db.sql first to create the database, or execute:
--   CREATE DATABASE IF NOT EXISTS healthassist CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Spring Boot auto-runs this file against the datasource configured in application.yml.

-- ============================================================
-- 1. NORMAL APPLICATION TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS doctors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    department VARCHAR(100) NOT NULL,
    specialty VARCHAR(200),
    available BOOLEAN DEFAULT TRUE,
    location VARCHAR(255),
    next_available_slot DATETIME,
    consultation_fee DECIMAL(10,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doctors_department (department),
    INDEX idx_doctors_specialty (specialty),
    INDEX idx_doctors_available (available)
);

CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_name VARCHAR(255) NOT NULL,
    patient_email VARCHAR(255),
    patient_phone VARCHAR(20),
    doctor_id BIGINT,
    doctor_name VARCHAR(255) NOT NULL,
    department VARCHAR(100) NOT NULL,
    appointment_date_time DATETIME NOT NULL,
    status VARCHAR(50) DEFAULT 'SCHEDULED',
    reason TEXT,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    INDEX idx_appointments_status (status),
    INDEX idx_appointments_department (department),
    INDEX idx_appointments_doctor (doctor_id),
    INDEX idx_appointments_datetime (appointment_date_time)
);

CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    status VARCHAR(50) DEFAULT 'OPEN',
    priority VARCHAR(50) DEFAULT 'MEDIUM',
    location VARCHAR(255),
    equipment_id VARCHAR(100),
    reported_by VARCHAR(255),
    assigned_to VARCHAR(255),
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tickets_status (status),
    INDEX idx_tickets_category (category),
    INDEX idx_tickets_priority (priority)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(100) NOT NULL,
    step_number INT NOT NULL,
    action VARCHAR(255) NOT NULL,
    agent_name VARCHAR(100),
    input_data TEXT,
    output_data TEXT,
    tool_calls TEXT,
    guardrail_results TEXT,
    risk_tier VARCHAR(20),
    escalated BOOLEAN DEFAULT FALSE,
    pii_detected BOOLEAN DEFAULT FALSE,
    moderation_flagged BOOLEAN DEFAULT FALSE,
    duration_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_workflow_id (workflow_id),
    INDEX idx_audit_created_at (created_at),
    INDEX idx_audit_agent (agent_name),
    INDEX idx_audit_risk (risk_tier),
    INDEX idx_audit_escalated (escalated)
);

-- ============================================================
-- 3. EMBEDDED / VECTOR DATA TABLES
-- ============================================================
-- These tables track ingested documents and their embedding
-- metadata. The actual vector embeddings are stored in the
-- SimpleVectorStore JSON file on disk; these tables provide
-- relational metadata, audit, and search capabilities.
-- ============================================================

-- Tracks every document source ingested into the vector store
CREATE TABLE IF NOT EXISTS document_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(500) NOT NULL,
    file_type VARCHAR(50) NOT NULL COMMENT 'PDF, TXT, etc.',
    file_size_bytes BIGINT DEFAULT 0,
    source_location VARCHAR(500) COMMENT 'classpath path or upload origin',
    category VARCHAR(100) COMMENT 'triage, insurance, facility, etc.',
    department VARCHAR(100),
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    chunk_count INT DEFAULT 0 COMMENT 'number of text chunks produced',
    status VARCHAR(50) DEFAULT 'INGESTED' COMMENT 'INGESTED, FAILED, DELETED',
    error_message TEXT,
    INDEX idx_docsrc_filename (filename),
    INDEX idx_docsrc_category (category),
    INDEX idx_docsrc_status (status),
    INDEX idx_docsrc_ingested (ingested_at)
);

-- Tracks individual text chunks and their embedding vectors
CREATE TABLE IF NOT EXISTS document_embeddings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL COMMENT 'FK to document_sources',
    chunk_index INT NOT NULL COMMENT 'order within the source document',
    chunk_text TEXT NOT NULL COMMENT 'the actual text content of the chunk',
    chunk_hash VARCHAR(64) COMMENT 'SHA-256 hash for deduplication',
    embedding_model VARCHAR(100) DEFAULT 'text-embedding-ada-002',
    embedding_dimensions INT DEFAULT 1536,
    token_count INT DEFAULT 0,
    metadata_json JSON COMMENT 'arbitrary key-value metadata as JSON',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES document_sources(id) ON DELETE CASCADE,
    INDEX idx_docemb_source (source_id),
    INDEX idx_docemb_hash (chunk_hash),
    INDEX idx_docemb_model (embedding_model)
);

-- Logs every vector-store search performed by the RAG pipeline
CREATE TABLE IF NOT EXISTS embedding_search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(100) COMMENT 'links to audit_logs.workflow_id',
    original_query TEXT NOT NULL,
    transformed_query TEXT COMMENT 'query after transformation',
    top_k INT DEFAULT 5,
    similarity_threshold DOUBLE DEFAULT 0.7,
    results_returned INT DEFAULT 0,
    search_duration_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_embsearch_workflow (workflow_id),
    INDEX idx_embsearch_created (created_at)
);

-- Chat session history for multi-turn conversations
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    user_identifier VARCHAR(255),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    message_count INT DEFAULT 0,
    mode VARCHAR(20) DEFAULT 'SYNC' COMMENT 'SYNC, STREAM, AGENT',
    INDEX idx_chat_session (session_id),
    INDEX idx_chat_last_activity (last_activity_at)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'USER, ASSISTANT, SYSTEM',
    content TEXT NOT NULL,
    workflow_id VARCHAR(100) COMMENT 'links to audit_logs if applicable',
    risk_tier VARCHAR(20),
    citations TEXT COMMENT 'comma-separated source references',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    INDEX idx_chatmsg_session (session_id),
    INDEX idx_chatmsg_role (role),
    INDEX idx_chatmsg_created (created_at)
);
