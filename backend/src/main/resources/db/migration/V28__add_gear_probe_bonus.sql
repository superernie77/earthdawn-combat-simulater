-- Sonstige Ausrüstung (GEAR) mit Probenbonus auf ein bestimmtes Talent/Fertigkeit (z.B. Leichte Stiefel)
ALTER TABLE character_equipment DROP CONSTRAINT IF EXISTS character_equipment_type_check;
ALTER TABLE character_equipment ADD CONSTRAINT character_equipment_type_check
    CHECK (type IN ('WEAPON', 'ARMOR', 'SHIELD', 'POTION', 'AMULET', 'VERBANDSZEUG', 'GEAR'));

ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS probe_bonus_talent_name VARCHAR(255);
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS probe_bonus_value INTEGER NOT NULL DEFAULT 0;
