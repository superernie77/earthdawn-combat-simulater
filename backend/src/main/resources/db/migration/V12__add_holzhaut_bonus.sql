-- V12: Holzhaut talent active bonus (added to Bewusstlosigkeits- und Todesschwelle)
ALTER TABLE characters ADD COLUMN IF NOT EXISTS holzhaut_bonus INTEGER NOT NULL DEFAULT 0;
