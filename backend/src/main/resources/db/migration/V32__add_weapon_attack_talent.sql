-- Zuordnung einer Waffe zu einem Angriffstalent/-fertigkeit (null = keine Zuordnung, bei jedem Angriff wählbar)
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS attack_talent_name VARCHAR(255);
