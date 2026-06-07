-- Phantomkrieger: Zauber mit Pflicht-Zielauswahl (auch BUFF-Typen die ein konkretes Ziel brauchen)
ALTER TABLE spell_definitions
    ADD COLUMN IF NOT EXISTS requires_target BOOLEAN NOT NULL DEFAULT FALSE;
