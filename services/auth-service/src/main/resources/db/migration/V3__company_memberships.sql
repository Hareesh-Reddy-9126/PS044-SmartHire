-- V3__company_memberships.sql
-- Company memberships table for multi-company recruitment platform.
-- Links users to companies with a role (COMPANY_ADMIN, RECRUITER) and status
-- (PENDING, ACTIVE, REJECTED, REVOKED, EXPIRED).
-- Runs with search_path = auth_svc (spring.flyway.default-schema).

CREATE TABLE company_memberships (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID         NOT NULL REFERENCES companies (id),
    user_id       UUID         NOT NULL REFERENCES users (id),
    role          VARCHAR(20)  NOT NULL
        CONSTRAINT company_memberships_role_chk CHECK (role IN ('COMPANY_ADMIN', 'RECRUITER')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT company_memberships_status_chk CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED')),
    invited_by    UUID         REFERENCES users (id),
    invited_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at   TIMESTAMPTZ,
    approved_by   UUID         REFERENCES users (id),
    invitation_token   VARCHAR(255),
    invitation_expires_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_company_memberships_company_id ON company_memberships (company_id);
CREATE INDEX idx_company_memberships_user_id ON company_memberships (user_id);
CREATE INDEX idx_company_memberships_status ON company_memberships (status);
CREATE INDEX idx_company_memberships_invitation_token ON company_memberships (invitation_token);
