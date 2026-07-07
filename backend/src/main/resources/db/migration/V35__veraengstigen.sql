-- Veraengstigen: Widerstands-Mindestwurf auf Effekten + Once-per-Round-Flag fuer die Widerstandsprobe
ALTER TABLE active_effects ADD COLUMN IF NOT EXISTS resist_target_number INTEGER;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS fear_resist_used_this_round BOOLEAN NOT NULL DEFAULT FALSE;
