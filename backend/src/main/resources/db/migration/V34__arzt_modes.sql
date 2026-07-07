-- Arzt-Umbau: zwei Behandlungsmodi (Verletzungen / Wunden)
-- Wundversorgung pro Wunde (Zaehler) statt pauschalem Flag; Verletzungsbehandlung 1x pro Erholungsprobe.
ALTER TABLE characters ADD COLUMN IF NOT EXISTS arzt_wounds_treated INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS arzt_injury_treated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE characters DROP COLUMN IF EXISTS arzt_wound_penalty_negated;
