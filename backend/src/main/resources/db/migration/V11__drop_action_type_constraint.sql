-- V11: Drop the action_type CHECK constraint entirely.
-- Validation is enforced at the JPA layer via @Enumerated(EnumType.STRING)
-- on the ActionType enum, so a DB-level constraint only causes maintenance
-- pain whenever new action types are added.
--
-- This block dynamically finds and drops ALL check constraints on
-- combat_logs.action_type, regardless of constraint name. This handles
-- cases where the constraint was created with a different name than
-- combat_logs_action_type_check.

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'combat_logs'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%action_type%'
    LOOP
        EXECUTE format('ALTER TABLE combat_logs DROP CONSTRAINT IF EXISTS %I', r.conname);
    END LOOP;
END $$;
