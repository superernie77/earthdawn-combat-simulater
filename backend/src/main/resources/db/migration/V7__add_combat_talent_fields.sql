-- V7: Add columns for Riposte, Tigersprung and Zweitwaffe talents
-- New fields on combatant_states added with the 4 new combat talents

ALTER TABLE combatant_states
    ADD COLUMN IF NOT EXISTS pending_riposte_attack_total  INTEGER NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS pending_riposte_attacker_id   BIGINT,
    ADD COLUMN IF NOT EXISTS tigersprung_used_this_round   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS zweit_waffe_used_this_round   BOOLEAN NOT NULL DEFAULT FALSE;
