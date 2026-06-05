-- V17: Rüstung/Schild: aktiv/inaktiv — nur ein Stück pro Typ zählt für Boni
ALTER TABLE character_equipment
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
