-- V4__company_membership_invitation_optional.sql
-- A self-requested membership has no inviter, so invited_at cannot be mandatory.
-- Invitations still set both invited_by and invited_at in the application layer.
ALTER TABLE company_memberships ALTER COLUMN invited_at DROP NOT NULL;
