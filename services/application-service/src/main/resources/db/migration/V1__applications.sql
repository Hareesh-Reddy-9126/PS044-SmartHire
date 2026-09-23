-- V1: application_svc schema, applications table (application-service). Forward-only, immutable once merged (governance §12).
-- Columns are exactly the Project-44 source fields for Application: application_id, job_id, candidate_id, resume_link, status.
-- Runs with search_path = application_svc (spring.flyway.default-schema), so unqualified names land in application_svc.

CREATE TABLE applications (
    application_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID         NOT NULL,
    candidate_id   UUID         NOT NULL,
    resume_link    VARCHAR(2048) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'APPLIED'
        CONSTRAINT applications_status_chk CHECK (status IN ('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'HIRED', 'REJECTED', 'WITHDRAWN'))
);

-- Index for candidate's own applications (status tracking)
CREATE INDEX idx_applications_candidate_status ON applications (candidate_id, status);

-- Index for recruiter listing by job (via job-service ownership)
CREATE INDEX idx_applications_job ON applications (job_id);

-- Index for status-based queries
CREATE INDEX idx_applications_status ON applications (status);