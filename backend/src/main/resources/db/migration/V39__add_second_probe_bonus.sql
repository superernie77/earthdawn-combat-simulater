-- Zweiter Probenbonus je GEAR-Gegenstand: manche Gegenstände wirken auf zwei Proben
-- (z.B. Espagrastiefel: +1 Heimlicher Schritt UND +2 Ausweichen).
ALTER TABLE character_equipment ADD COLUMN probe_bonus_talent_name2 VARCHAR(255);
ALTER TABLE character_equipment ADD COLUMN probe_bonus_value2 INTEGER NOT NULL DEFAULT 0;
