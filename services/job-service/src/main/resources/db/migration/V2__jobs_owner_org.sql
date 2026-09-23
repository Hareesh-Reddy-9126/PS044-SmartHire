-- V2: add owner_org_id to jobs (Inc 2 ownership). Forward-only; V1 is never edited (governance §12).
--
-- owner_org_id is the UUID of the organization that owns the posting, taken from the verified JWT
-- orgId claim at creation time and compared against this stored value on every mutation. It is a
-- LOGICAL reference to auth_svc.organizations: no foreign key, because each service owns its own
-- schema and a cross-schema FK would couple job-service's write path to auth-service's tables
-- (ADR-0007). Integrity is enforced at the boundary — only a token auth-service signed can supply a
-- value — not by the database.

-- Three steps rather than one, so no permanent DEFAULT is left behind. A column default of the demo
-- organization would mean any future INSERT that omitted owner_org_id silently produced a job owned
-- by that tenant, which is exactly the ownership-forgery the check is meant to prevent.
ALTER TABLE jobs ADD COLUMN owner_org_id UUID;

-- Backfill: the two Inc 0 seed rows predate ownership. '00000000-0000-0000-0000-0000000000a1' is the
-- organization actually seeded by auth-service V1__auth.sql ('SmartHire Demo Org') — not an invented
-- identifier — so the demo recruiter owns the demo jobs and the seeded data stays coherent.
UPDATE jobs SET owner_org_id = '00000000-0000-0000-0000-0000000000a1' WHERE owner_org_id IS NULL;

ALTER TABLE jobs ALTER COLUMN owner_org_id SET NOT NULL;

-- Public listing: every anonymous and candidate query filters on status = 'OPEN'.
CREATE INDEX idx_jobs_status ON jobs (status);

-- Recruiter listing: the owning organization's postings, optionally narrowed by status. Leading with
-- owner_org_id also serves lookups by owner alone, so a separate single-column index would be
-- redundant.
CREATE INDEX idx_jobs_owner_status ON jobs (owner_org_id, status);

-- The V1 jobs_status_chk CHECK constraint is untouched: DRAFT/OPEN/CLOSED remain the only values,
-- and the Inc 2 state machine is a narrowing of that set, not a change to it.
