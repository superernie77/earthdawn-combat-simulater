-- Fehlende Spalten in character_equipment (Schilde, Rüstungsmalus, Tränke)
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS initiative_penalty     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS physical_defense_bonus INTEGER NOT NULL DEFAULT 0;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS mystic_defense_bonus   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS quantity               INTEGER NOT NULL DEFAULT 1;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS heal_step              INTEGER NOT NULL DEFAULT 0;
