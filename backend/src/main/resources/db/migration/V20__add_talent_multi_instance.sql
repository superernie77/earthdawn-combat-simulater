-- Zaubermatritze: Talente die mehrfach gelernt werden können und deren Rang dem Kreis entspricht
ALTER TABLE talent_definitions
    ADD COLUMN IF NOT EXISTS max_instances  INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS rank_from_circle BOOLEAN NOT NULL DEFAULT FALSE;
