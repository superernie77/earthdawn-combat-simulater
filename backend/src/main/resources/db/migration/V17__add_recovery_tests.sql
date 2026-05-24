-- Erholungsproben: verbleibende Proben pro Tag (NULL = voll)
ALTER TABLE characters ADD COLUMN IF NOT EXISTS recovery_tests_remaining INTEGER;

-- Tränke: Heiltrank = true (Extra-Probe), Erholungstrank = false (verbraucht normale Probe)
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS extra_recovery BOOLEAN NOT NULL DEFAULT FALSE;
