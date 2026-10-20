-- ai_svc.V1__match_scores.sql
-- Initial schema for AI Service match scores
-- Part of Inc 7: AI-assisted recruitment (advisory, human-in-the-loop)

CREATE SCHEMA IF NOT EXISTS ai_svc;

CREATE TABLE ai_svc.match_scores (
    match_score_id   UUID PRIMARY KEY,
    application_id   UUID NOT NULL,
    job_id           UUID NOT NULL,
    score            SMALLINT NOT NULL CHECK (score >= 0 AND score <= 100),
    rationale        TEXT NOT NULL,
    model            VARCHAR(100) NOT NULL,
    prompt_version   VARCHAR(50) NOT NULL,
    confidence       SMALLINT CHECK (confidence >= 0 AND confidence <= 100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    overridden_by    UUID,
    overridden_at    TIMESTAMPTZ,
    override_reason  TEXT
);

-- Index for common query patterns
CREATE INDEX idx_match_scores_application ON ai_svc.match_scores (application_id);
CREATE INDEX idx_match_scores_job ON ai_svc.match_scores (job_id);
CREATE INDEX idx_match_scores_created_at ON ai_svc.match_scores (created_at);

-- Add comment for audit trail clarity
COMMENT ON TABLE ai_svc.match_scores IS 'AI-generated match scores — advisory only, never used for autonomous decisions (ADR-0015). Human-in-the-loop required for any recruitment action.';
COMMENT ON COLUMN ai_svc.match_scores.overridden_by IS 'Recruiter UUID who overrode the AI recommendation';
COMMENT ON COLUMN ai_svc.match_scores.overridden_at IS 'Timestamp when override occurred';
COMMENT ON COLUMN ai_svc.match_scores.override_reason IS 'Reason provided by recruiter for overriding AI recommendation';