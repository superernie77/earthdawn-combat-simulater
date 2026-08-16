-- Kobrastoß: freie Aktion in der Ansagephase, ersetzt die eigene Initiativeprobe durch
-- die Kobrastoßstufe (Rang + GES) und gewährt +2 je Erfolg auf den ersten Angriff gegen
-- den angesagten Gegner in derselben Runde.
ALTER TABLE combatant_states ADD COLUMN kobrastoss_used_this_round BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combatant_states ADD COLUMN kobrastoss_target_id BIGINT DEFAULT -1;
ALTER TABLE combatant_states ADD COLUMN pending_kobrastoss_bonus INTEGER NOT NULL DEFAULT 0;
