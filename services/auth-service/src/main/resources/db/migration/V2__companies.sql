-- V2__companies.sql
-- Companies table for multi-company recruitment platform.
-- Runs with search_path = auth_svc (spring.flyway.default-schema).
-- Each company represents an independent recruiting organization that can post jobs,
# recruit candidates, and manage its own recruiters and administrators.
-- Status lifecycle: PENDING_APPROVAL -> ACTIVE/REJECTED; ACTIVE can be SUSPENDED/reactivated.

CREATE TABLE companies (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVAL'
        CONSTRAINT companies_status_chk CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'SUSPENDED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    approved_by UUID         REFERENCES users (id),
    rejection_reason VARCHAR(500)
);

CREATE INDEX idx_companies_status ON companies (status);
CREATE INDEX idx_companies_name ON companies (name);

-- Index for looking up companies by owner
CREATE INDEX idx_companies_approved_by ON companies (approved_by);
