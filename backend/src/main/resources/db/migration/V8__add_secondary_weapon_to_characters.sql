-- V8: Secondary weapon selection for Zweitwaffe talent
ALTER TABLE characters ADD COLUMN IF NOT EXISTS secondary_weapon_id BIGINT;
