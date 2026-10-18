-- V5: align entry_exit_logs with the entity (audit timestamp).
ALTER TABLE entry_exit_logs ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
