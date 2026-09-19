-- V1: auth_svc schema (auth-service). Forward-only, immutable once merged (governance §12).
-- Owns identity + RBAC + refresh-token rotation state (ADR-0009/0010/0011).
-- Runs with search_path = auth_svc (spring.flyway.default-schema), so unqualified names land there.
--
-- SECURITY (decision 8): this migration contains NO passwords. User accounts (and their BCrypt
-- password hashes) are created at runtime — public self-registration (CANDIDATE) or the env-driven
-- dev seeder (RECRUITER/ADMIN). Only a non-secret demo organization row is seeded here.

CREATE TABLE organizations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    -- Stores the PasswordEncoder-prefixed hash, e.g. "{bcrypt}$2a$12$..." — never a plaintext password.
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL
        CONSTRAINT users_role_chk CHECK (role IN ('CANDIDATE', 'RECRUITER', 'ADMIN')),
    -- Recruiters/admins may belong to an organization; candidates do not (nullable).
    org_id        UUID         REFERENCES organizations (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_org_id ON users (org_id);

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id),
    -- Rotation lineage: every rotated token keeps its family id so replay of any superseded token
    -- can revoke the whole family (reuse detection, ADR-0009).
    family_id   UUID         NOT NULL,
    -- SHA-256 hex of the opaque refresh token; the raw token is never stored (governance §17).
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT false,
    replaced_by UUID         REFERENCES refresh_tokens (id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

-- Non-secret demo organization so the dev seeder can attach a RECRUITER to a stable org id.
INSERT INTO organizations (id, name) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'SmartHire Demo Org');
