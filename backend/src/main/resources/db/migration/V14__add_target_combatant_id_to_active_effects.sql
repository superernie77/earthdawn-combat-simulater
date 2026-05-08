-- V14: Ziel-spezifische Effekte (z.B. Schwachstelle erkennen)
ALTER TABLE active_effects ADD COLUMN IF NOT EXISTS target_combatant_id BIGINT;
