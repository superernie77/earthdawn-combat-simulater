-- V13: Krallenhand-Markierung auf Equipment (auto-verwaltet vom Talent)
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS claw_weapon BOOLEAN NOT NULL DEFAULT FALSE;
