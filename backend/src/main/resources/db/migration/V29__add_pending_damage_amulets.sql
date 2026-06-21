-- Verzweiflungsschlag-Schaden-Amulette: aufgeschobenes Entladen bei ausstehendem Treffer
-- (Amulett wird erst entladen, wenn der Schaden tatsächlich angewendet wird).
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_damage_amulet_ids TEXT;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS pending_damage_amulet_attacker_id BIGINT;
