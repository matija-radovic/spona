CREATE TABLE organization_member (
  user_id         uuid        NOT NULL REFERENCES app_user (id)     ON DELETE CASCADE,
  organization_id uuid        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  joined_at       timestamptz NOT NULL DEFAULT now(),

  PRIMARY KEY (user_id, organization_id)
);
CREATE INDEX organization_member_org_idx ON organization_member (organization_id);

CREATE TABLE role_assignment (
  id              uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid              NOT NULL,
  organization_id uuid              NOT NULL,
  role            organization_role NOT NULL,
  assigned_at     timestamptz       NOT NULL DEFAULT now(),
  revoked_at      timestamptz,
  -- Not part of the composition: a different person, who may delete their
  -- account without that deleting this row.
  assigned_by     uuid              REFERENCES app_user (id) ON DELETE SET NULL,

  FOREIGN KEY (user_id, organization_id)
    REFERENCES organization_member (user_id, organization_id) ON DELETE CASCADE,
  CONSTRAINT role_assignment_revoked_check CHECK (revoked_at IS NULL OR revoked_at >= assigned_at)
);
CREATE UNIQUE INDEX role_assignment_live_key  ON role_assignment (organization_id, user_id)
  WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX role_assignment_owner_key ON role_assignment (organization_id)
  WHERE role = 'OWNER' AND revoked_at IS NULL;
CREATE INDEX role_assignment_history_idx ON role_assignment (organization_id, user_id, assigned_at DESC);

CREATE TABLE organization_invitations (
  id              uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid              NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
  role            organization_role NOT NULL,
  invitee_user_id uuid              REFERENCES app_user (id) ON DELETE CASCADE,
  invitee_email   text,                       -- lowercased; a snapshot for a username invite
  invited_by_id   uuid              REFERENCES app_user (id) ON DELETE SET NULL,
  status          invitation_status NOT NULL,
  created_at      timestamptz       NOT NULL DEFAULT now(),
  resolved_at     timestamptz,
  dismissed_at    timestamptz,

  CONSTRAINT invitation_target_check CHECK (
    invitee_user_id IS NOT NULL OR invitee_email IS NOT NULL),
  CONSTRAINT invitation_email_length_check CHECK (
    invitee_email IS NULL OR char_length(invitee_email) BETWEEN 1 AND 254),
  CONSTRAINT invitation_role_check      CHECK (role <> 'OWNER'),
  CONSTRAINT invitation_resolved_check  CHECK ((status = 'WAITING') = (resolved_at IS NULL)),
  CONSTRAINT invitation_dismissed_check CHECK (dismissed_at IS NULL OR status = 'DECLINED')
);
CREATE UNIQUE INDEX invitation_waiting_user_key  ON organization_invitations (organization_id, invitee_user_id)
  WHERE status = 'WAITING' AND invitee_user_id IS NOT NULL;
CREATE UNIQUE INDEX invitation_waiting_email_key ON organization_invitations (organization_id, lower(invitee_email))
  WHERE status = 'WAITING' AND invitee_user_id IS NULL;

CREATE INDEX invitation_invitee_idx ON organization_invitations (invitee_user_id, status);
CREATE INDEX invitation_org_idx     ON organization_invitations (organization_id, status);
CREATE INDEX invitation_email_idx ON organization_invitations (lower(invitee_email))
  WHERE status = 'WAITING' AND invitee_user_id IS NULL;
