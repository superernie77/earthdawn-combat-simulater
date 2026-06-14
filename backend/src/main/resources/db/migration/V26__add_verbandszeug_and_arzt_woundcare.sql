-- Arzt: Verbandszeug-Verbrauchsgegenstand + Aufheben des Wundmalus auf die nächste Erholungsprobe

-- Neuer Equipment-Typ VERBANDSZEUG
ALTER TABLE character_equipment DROP CONSTRAINT IF EXISTS character_equipment_type_check;
ALTER TABLE character_equipment ADD CONSTRAINT character_equipment_type_check
    CHECK (type IN ('WEAPON', 'ARMOR', 'SHIELD', 'POTION', 'AMULET', 'VERBANDSZEUG'));

-- Merker: nächste Erholungsprobe ohne Wundabzug (nach erfolgreicher Arztbehandlung)
ALTER TABLE characters ADD COLUMN IF NOT EXISTS arzt_wound_penalty_negated BOOLEAN NOT NULL DEFAULT FALSE;
