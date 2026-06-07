-- Zaubermatritze: zugewiesener Zauber pro Matrix-Instanz
ALTER TABLE character_talents
    ADD COLUMN IF NOT EXISTS assigned_spell_id BIGINT REFERENCES spell_definitions(id) ON DELETE SET NULL;
