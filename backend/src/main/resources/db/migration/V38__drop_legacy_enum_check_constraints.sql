-- Prod-Hotfix: Alt-Datenbanken (vor Flyway von Hibernate ddl-auto angelegt) tragen
-- CHECK-Constraints auf Enum-Spalten, die nur die damaligen Werte erlauben —
-- z.B. spell_definitions_modify_stat_check ohne DODGE_STEP/MOVEMENT_HEXES.
-- Jede neue Enum-Konstante bricht dort den Start (Boot-Schleife → Dauer-CPU).
--
-- Die Flyway-Baseline (V1) definiert selbst KEINE CHECK-Constraints; alles vom Typ
-- CHECK auf den App-Tabellen stammt aus der Hibernate-Ära und kann weg. Die
-- Wertebereichs-Sicherheit liegt in der Anwendung (@Enumerated(EnumType.STRING)).
-- Auf per Baseline erzeugten Datenbanken ist diese Migration ein No-op.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname
        FROM pg_constraint
        WHERE contype = 'c'
          AND connamespace = 'public'::regnamespace
          AND conrelid::regclass::text IN (
              'characters', 'character_talents', 'character_skills', 'character_spells',
              'character_equipment', 'spell_definitions', 'talent_definitions',
              'skill_definitions', 'discipline_definitions', 'combat_sessions',
              'combatant_states', 'combat_logs', 'active_effects',
              'active_effect_modifiers', 'talent_passive_modifiers', 'user_accounts')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
        RAISE NOTICE 'Dropped legacy check constraint % on %', r.conname, r.tbl;
    END LOOP;
END $$;
