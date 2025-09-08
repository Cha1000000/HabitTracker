-- PostgreSQL schema for HabitTracker (Symfony 7 + JWT)
-- Timezone-aware timestamps use TIMESTAMPTZ

CREATE EXTENSION IF NOT EXISTS citext;

-- users
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  email CITEXT UNIQUE NOT NULL,
  avatar_url TEXT,
  avatar_thumbnail_url TEXT,
  level INT NOT NULL DEFAULT 1,
  points INT NOT NULL DEFAULT 0,
  streak INT NOT NULL DEFAULT 0,
  longest_streak INT NOT NULL DEFAULT 0,
  friends_count INT NOT NULL DEFAULT 0,
  timezone TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  last_active_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

-- unique FCM tokens per user
CREATE TABLE IF NOT EXISTS user_fcm_tokens (
  token TEXT PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_fcm_tokens_user ON user_fcm_tokens(user_id);

-- habits
CREATE TABLE IF NOT EXISTS habits (
  id UUID PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  icon TEXT NOT NULL,
  color TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('PERSONAL','GROUP')),
  frequency TEXT NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','WEEKDAYS','WEEKENDS','CUSTOM')),
  reminder_hour SMALLINT,
  reminder_minute SMALLINT,
  target_value INT NOT NULL DEFAULT 1,
  unit TEXT NOT NULL,
  created_by UUID REFERENCES users(id),
  max_participants INT NOT NULL DEFAULT 10,
  invite_code TEXT UNIQUE,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

-- group participants
CREATE TABLE IF NOT EXISTS habit_participants (
  habit_id UUID NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (habit_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_habit_participants_user ON habit_participants(user_id);

-- habit entries
CREATE TABLE IF NOT EXISTS habit_entries (
  habit_id UUID NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  completed BOOLEAN NOT NULL,
  completed_at TIMESTAMPTZ,
  value INT NOT NULL DEFAULT 1,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (habit_id, user_id, date)
);
CREATE INDEX IF NOT EXISTS idx_habit_entries_user ON habit_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_habit_entries_date ON habit_entries(date);

-- friendships
CREATE TABLE IF NOT EXISTS friendships (
  user_id1 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_id2 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','BLOCKED','DECLINED')),
  requested_by UUID REFERENCES users(id),
  requested_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  blocked_by UUID REFERENCES users(id),
  blocked_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id1, user_id2)
);

-- friend requests
CREATE TABLE IF NOT EXISTS friend_requests (
  id UUID PRIMARY KEY,
  from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),
  message TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  responded_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_friend_request_unique_pair
ON friend_requests(from_user_id, to_user_id)
WHERE status = 'PENDING';

-- habit invitations
CREATE TABLE IF NOT EXISTS habit_invitations (
  id UUID PRIMARY KEY,
  habit_id UUID NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
  habit_title TEXT NOT NULL,
  from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  from_user_name TEXT NOT NULL,
  to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message TEXT,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL,
  responded_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_habit_invitations_to ON habit_invitations(to_user_id, status);
CREATE INDEX IF NOT EXISTS idx_habit_invitations_from ON habit_invitations(from_user_id, status);

-- housekeeping helpers
-- Soft-deleted habits (is_active=false) can be physically removed later by cron.
