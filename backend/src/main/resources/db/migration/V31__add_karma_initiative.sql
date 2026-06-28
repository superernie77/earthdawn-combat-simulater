-- Karma auf Initiative (Disziplin-Fähigkeit ab Kreis 3): in der Ansagephase gewählt,
-- beim Initiativewurf wird 1 Karma ausgegeben und ein W6 (Stufe 4) addiert.
ALTER TABLE combatant_states ADD COLUMN IF NOT EXISTS karma_initiative_this_round BOOLEAN NOT NULL DEFAULT FALSE;
