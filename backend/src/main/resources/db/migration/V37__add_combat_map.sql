-- Hexfeld-Kampfkarte (optional pro Session; bestehende Kämpfe bleiben unberührt)

-- Karte an der Session
ALTER TABLE combat_sessions ADD COLUMN map_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE combat_sessions ADD COLUMN map_width  INTEGER NOT NULL DEFAULT 24;
ALTER TABLE combat_sessions ADD COLUMN map_height INTEGER NOT NULL DEFAULT 16;

-- Position + Bewegungsbudget des Kombattanten (NULL = nicht platziert)
ALTER TABLE combatant_states ADD COLUMN map_q INTEGER;
ALTER TABLE combatant_states ADD COLUMN map_r INTEGER;
ALTER TABLE combatant_states ADD COLUMN moved_hexes_this_round INTEGER NOT NULL DEFAULT 0;

-- Hindernisse (Wand, Tür, Baum, Fels, Möbel)
CREATE TABLE map_obstacles (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT      NOT NULL REFERENCES combat_sessions (id) ON DELETE CASCADE,
    obstacle_type VARCHAR(20) NOT NULL,
    q             INTEGER     NOT NULL,
    r             INTEGER     NOT NULL,
    door_open     BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_map_obstacles_session ON map_obstacles (session_id);

-- Bewegungsrate in Feldern auf dem Charakterbogen
ALTER TABLE characters ADD COLUMN movement_hexes INTEGER NOT NULL DEFAULT 8;

-- Reichweiten für Projektil-/Wurfwaffen (in Feldern; NULL = keine Fernkampfwaffe)
ALTER TABLE character_equipment ADD COLUMN range_short  INTEGER;
ALTER TABLE character_equipment ADD COLUMN range_medium INTEGER;
ALTER TABLE character_equipment ADD COLUMN range_long   INTEGER;

-- Zauberreichweite in Feldern (0 = Selbst/Berührung)
ALTER TABLE spell_definitions ADD COLUMN range_hexes INTEGER NOT NULL DEFAULT 10;
-- Zauber ohne Zielwahl (Selbst-Buffs/Heilungen mit fester Schwierigkeit) wirken auf Berührung
UPDATE spell_definitions SET range_hexes = 0
 WHERE requires_target = FALSE AND effect_type IN ('BUFF', 'HEAL');
