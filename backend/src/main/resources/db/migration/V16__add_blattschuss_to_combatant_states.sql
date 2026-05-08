-- V16: Blattschuss-Talent — pending Multi-Karma-Wurf nach Fehlschlag
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS blattschuss_used_this_round BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_blattschuss_defender_id BIGINT NOT NULL DEFAULT -1;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_blattschuss_total INTEGER NOT NULL DEFAULT 0;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_blattschuss_karma_used INTEGER NOT NULL DEFAULT 0;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_blattschuss_rank INTEGER NOT NULL DEFAULT 0;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_blattschuss_weapon_id BIGINT NOT NULL DEFAULT -1;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_blattschuss_defense INTEGER NOT NULL DEFAULT 0;
