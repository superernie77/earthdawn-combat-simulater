-- T'skrang-Schwanzangriff: Schwanzwaffen-Flag auf Ausrüstung + Once-per-Round-Flag auf Kombattanten
ALTER TABLE character_equipment ADD COLUMN IF NOT EXISTS tail_weapon BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS schwanzangriff_used_this_round BOOLEAN NOT NULL DEFAULT FALSE;
