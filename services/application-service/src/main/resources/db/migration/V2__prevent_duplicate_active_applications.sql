-- V2: enforce at most one active application per (job, candidate) at the database level.
-- Service-level guard rejects duplicates with 409; this partial unique index is the backstop against
-- concurrent requests that both pass the existence check. WITHDRAWN and REJECTED are terminal, so a
-- candidate may re-apply after either. Forward-only, immutable once merged (governance §12).
-- Runs with search_path = application_svc (spring.flyway.default-schema).

CREATE UNIQUE INDEX uq_applications_active_job_candidate
    ON applications (job_id, candidate_id)
    WHERE status NOT IN ('WITHDRAWN', 'REJECTED');
