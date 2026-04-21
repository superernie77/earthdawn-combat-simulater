-- Enum-Check-Constraint aktualisieren: SHIELD und POTION ergänzen
ALTER TABLE character_equipment DROP CONSTRAINT IF EXISTS character_equipment_type_check;
ALTER TABLE character_equipment ADD CONSTRAINT character_equipment_type_check
    CHECK (type IN ('WEAPON', 'ARMOR', 'SHIELD', 'POTION'));
