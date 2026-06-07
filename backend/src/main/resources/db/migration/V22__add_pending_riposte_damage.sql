ALTER TABLE combatant_states
    ADD COLUMN IF NOT EXISTS pending_riposte_damage INTEGER NOT NULL DEFAULT 0;
