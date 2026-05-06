-- V9: Add new action types (RIPOSTE, MANOEUVER, TIGERSPRUNG, ZWEITE_WAFFE) to combat_logs check constraint

ALTER TABLE combat_logs DROP CONSTRAINT IF EXISTS combat_logs_action_type_check;

ALTER TABLE combat_logs ADD CONSTRAINT combat_logs_action_type_check
    CHECK (action_type IN (
        'MELEE_ATTACK', 'RANGED_ATTACK', 'SPELL_ATTACK',
        'TALENT_TEST', 'SKILL_TEST', 'RECOVERY_TEST',
        'INITIATIVE', 'EFFECT_ADDED', 'EFFECT_REMOVED',
        'VALUE_CHANGED', 'ROUND_CHANGE', 'COMBAT_OPTION',
        'FREE_ACTION', 'DODGE', 'STAND_UP', 'AUFSPRINGEN',
        'THREADWEAVE', 'SPELL_CAST', 'TAUNT',
        'ACROBATIC_DEFENSE', 'COMBAT_SENSE', 'DISTRACT', 'IRON_WILL',
        'RIPOSTE', 'MANOEUVER', 'TIGERSPRUNG', 'ZWEITE_WAFFE'
    ));
