-- V1: recruitment_svc schema, recruitments table (recruitment-service). Forward-only, immutable once merged (governance §12).
-- Runs with search_path = recruitment_svc (spring.flyway.default-schema), so unqualified names land in recruitment_svc.

CREATE TABLE recruitments (
    recruitment_id    UUID         PRIMARY KEY,
    application_id    UUID         NOT NULL,

    -- Ownership, resolved once at shortlist time by following Application -> Job and then stored
    -- here. Every later authorization decision is a local column comparison (JWT orgId vs
    -- owner_org_id, JWT sub vs candidate_id) rather than a pair of cross-service calls, which is
    -- the same "stored owning organization" shape job_svc.jobs uses. Safe to denormalize because
    -- neither value is editable upstream: a job's owning organization is fixed at creation
    -- (JobController never exposes it for update) and an application's candidate is write-once.
    --
    -- No cross-schema foreign key to application_svc or job_svc: services own their schemas and a
    -- database-level dependency between them would defeat that boundary (architecture §7).
    job_id            UUID         NOT NULL,
    owner_org_id      UUID         NOT NULL,
    candidate_id      UUID         NOT NULL,

    interview_status  VARCHAR(20)  NOT NULL DEFAULT 'NOT_SCHEDULED'
        CONSTRAINT recruitments_interview_status_chk CHECK (interview_status IN ('NOT_SCHEDULED', 'SCHEDULED', 'COMPLETED', 'NO_SHOW')),
    final_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT recruitments_final_status_chk CHECK (final_status IN ('PENDING', 'HIRED', 'REJECTED')),

    -- One recruitment per application. Shortlisting is therefore idempotent at the database level:
    -- two concurrent POSTs for the same application cannot both succeed, whatever the service-level
    -- existence check does under a race.
    CONSTRAINT recruitments_application_uk UNIQUE (application_id)
);

-- The two authorization-scoped listings, and nothing speculative beyond them: every index here
-- backs a query the service actually issues.

-- Recruiter listing/authorization path: "the recruitments my organization owns".
CREATE INDEX idx_recruitments_owner_org ON recruitments (owner_org_id);

-- Candidate status-tracking path (AC-5): "the recruitments that belong to me".
CREATE INDEX idx_recruitments_candidate ON recruitments (candidate_id);
