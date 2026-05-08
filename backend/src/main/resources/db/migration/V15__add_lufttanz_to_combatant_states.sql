-- V15: Lufttanz-Talent — Aktivierungs-Flag und pending Bonusangriff
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS lufttanz_activated_this_round BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_lufttanz_target_id BIGINT NOT NULL DEFAULT -1;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_lufttanz_weapon_id BIGINT NOT NULL DEFAULT -1;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS lufttanz_bonus_used_this_round BOOLEAN NOT NULL DEFAULT FALSE;
