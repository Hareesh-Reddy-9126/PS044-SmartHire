-- V1__documents.sql
-- Document Service initial schema

CREATE SCHEMA IF NOT EXISTS document_svc;

CREATE TABLE document_svc.documents (
    document_id         UUID PRIMARY KEY,
    candidate_id        UUID NOT NULL,
    document_type       VARCHAR(50) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    file_size           BIGINT NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_candidate_id ON document_svc.documents(candidate_id);
CREATE INDEX idx_documents_created_at ON document_svc.documents(created_at);

COMMENT ON TABLE document_svc.documents IS 'Document metadata for candidate uploads (resumes, etc.)';
COMMENT ON COLUMN document_svc.documents.document_id IS 'Primary key, UUID';
COMMENT ON COLUMN document_svc.documents.candidate_id IS 'Candidate who owns this document (from JWT sub)';
COMMENT ON COLUMN document_svc.documents.document_type IS 'Type of document (e.g., RESUME)';
COMMENT ON COLUMN document_svc.documents.file_name IS 'Original uploaded file name';
COMMENT ON COLUMN document_svc.documents.content_type IS 'MIME type of the file';
COMMENT ON COLUMN document_svc.documents.storage_key IS 'Storage abstraction key/path';
COMMENT ON COLUMN document_svc.documents.file_size IS 'File size in bytes';
COMMENT ON COLUMN document_svc.documents.created_at IS 'Creation timestamp';
COMMENT ON COLUMN document_svc.documents.updated_at IS 'Last update timestamp';