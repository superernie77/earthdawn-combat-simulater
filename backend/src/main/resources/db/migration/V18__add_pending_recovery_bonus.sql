-- Ausstehender Bonus-Stufen auf die naechste regulaere Erholungsprobe (durch Erholungstrank)
ALTER TABLE characters ADD COLUMN IF NOT EXISTS pending_recovery_bonus INTEGER NOT NULL DEFAULT 0;
