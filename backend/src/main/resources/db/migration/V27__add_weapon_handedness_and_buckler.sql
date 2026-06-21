-- Ein-/Zweihändige Waffen + Buckler-Schild + Auto-Ablegen des Schilds
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS two_handed  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS buckler     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS auto_stowed BOOLEAN NOT NULL DEFAULT FALSE;
