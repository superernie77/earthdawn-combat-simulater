-- Konfigurierbare Boni/Mali auf Lebenspunkte, Initiative und Erholungsstufe
ALTER TABLE characters ADD COLUMN IF NOT EXISTS health_bonus     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS initiative_bonus INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS recovery_bonus   INTEGER NOT NULL DEFAULT 0;
