-- V1: job_svc schema, jobs table (job-service). Forward-only, immutable once merged (governance §12).
-- Columns are exactly the Project-44 source fields for Job: job_id, company_name, role, location, status.
-- Runs with search_path = job_svc (spring.flyway.default-schema), so unqualified names land in job_svc.

CREATE TABLE jobs (
    job_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    role         VARCHAR(255) NOT NULL,
    location     VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT jobs_status_chk CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

-- Inc 0 demonstration seed data: proves the client → gateway → discovery → job-service → DB path
-- returns real rows. Fixed UUIDs keep the Testcontainers integration test deterministic.
INSERT INTO jobs (job_id, company_name, role, location, status) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme Corp',  'Backend Engineer', 'Remote',        'OPEN'),
    ('22222222-2222-2222-2222-222222222222', 'Globex Inc', 'Data Analyst',     'Bengaluru, IN', 'OPEN');
