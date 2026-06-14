-- Nachtreten: Zusatz-Waffenlos-Angriff mit eigenem Once-per-Round-Flag
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS nachtreten_used_this_round BOOLEAN NOT NULL DEFAULT FALSE;
