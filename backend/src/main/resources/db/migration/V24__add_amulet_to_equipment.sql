-- Verzweiflungsschlag-Amulette: neuer Equipment-Typ AMULET + Amulett-Felder

-- Enum-Check-Constraint um AMULET erweitern
ALTER TABLE character_equipment DROP CONSTRAINT IF EXISTS character_equipment_type_check;
ALTER TABLE character_equipment ADD CONSTRAINT character_equipment_type_check
    CHECK (type IN ('WEAPON', 'ARMOR', 'SHIELD', 'POTION', 'AMULET'));

-- Amulett-spezifische Spalten
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS charged BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS amulet_for_spell BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS amulet_step_bonus INTEGER NOT NULL DEFAULT 0;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS blood_magic_damage INTEGER NOT NULL DEFAULT 0;
