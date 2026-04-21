-- Flyway baseline: vollständiges Schema (läuft nur auf frischen Datenbanken)
-- Bestehende Prod-DBs werden per baseline-on-migrate auf V1 gesetzt ohne dieses Skript auszuführen.

CREATE TABLE discipline_definitions (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL UNIQUE,
    karma_step          INTEGER,
    bw_bonus_per_circle INTEGER DEFAULT 5,
    td_bonus_per_circle INTEGER DEFAULT 6,
    description         VARCHAR(1000)
);

CREATE TABLE discipline_access_talent_names (
    discipline_id BIGINT NOT NULL REFERENCES discipline_definitions(id),
    talent_name   VARCHAR(255)
);

CREATE TABLE talent_definitions (
    id                           BIGSERIAL PRIMARY KEY,
    name                         VARCHAR(255) NOT NULL UNIQUE,
    attribute                    VARCHAR(50),
    description                  VARCHAR(1000),
    testable                     BOOLEAN DEFAULT TRUE,
    attack_talent                BOOLEAN DEFAULT FALSE,
    rank_scaled                  BOOLEAN DEFAULT FALSE,
    free_action                  BOOLEAN DEFAULT FALSE,
    free_action_test_stat        VARCHAR(50),
    free_action_effect_target    VARCHAR(50),
    free_action_modify_stat      VARCHAR(50),
    free_action_trigger_context  VARCHAR(50),
    free_action_value_per_success DOUBLE PRECISION DEFAULT 0,
    free_action_duration         INTEGER DEFAULT 1,
    free_action_damage_cost      INTEGER DEFAULT 0
);

CREATE TABLE talent_passive_modifiers (
    talent_id       BIGINT NOT NULL REFERENCES talent_definitions(id),
    target_stat     VARCHAR(50),
    mod_operation   VARCHAR(50),
    mod_value       DOUBLE PRECISION,
    trigger_context VARCHAR(50),
    mod_description VARCHAR(255)
);

CREATE TABLE skill_definitions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    attribute   VARCHAR(50),
    description VARCHAR(1000),
    category    VARCHAR(255)
);

CREATE TABLE spell_definitions (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    discipline           VARCHAR(255),
    circle               INTEGER,
    threads              INTEGER DEFAULT 0,
    weaving_difficulty   INTEGER DEFAULT 0,
    casting_difficulty   INTEGER DEFAULT 0,
    effect_type          VARCHAR(50) NOT NULL,
    effect_step          INTEGER DEFAULT 0,
    use_mystic_armor     BOOLEAN DEFAULT TRUE,
    modify_stat          VARCHAR(50),
    modify_operation     VARCHAR(50),
    modify_value         DOUBLE PRECISION DEFAULT 0,
    modify_trigger       VARCHAR(50),
    duration             INTEGER DEFAULT 0,
    description          VARCHAR(1000),
    effect_description   VARCHAR(200),
    extra_success_effect VARCHAR(20) DEFAULT 'NONE'
);

CREATE TABLE characters (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    player_name           VARCHAR(255),
    circle                INTEGER,
    legend_points         BIGINT,
    discipline_id         BIGINT REFERENCES discipline_definitions(id),
    dexterity             INTEGER,
    strength              INTEGER,
    toughness             INTEGER,
    perception            INTEGER,
    willpower             INTEGER,
    charisma              INTEGER,
    physical_defense      INTEGER,
    spell_defense         INTEGER,
    social_defense        INTEGER,
    wound_threshold       INTEGER,
    physical_defense_bonus INTEGER DEFAULT 0,
    spell_defense_bonus   INTEGER DEFAULT 0,
    social_defense_bonus  INTEGER DEFAULT 0,
    unconsciousness_rating INTEGER,
    death_rating          INTEGER,
    physical_armor        INTEGER,
    mystic_armor          INTEGER,
    weapon_name           VARCHAR(255),
    weapon_damage_step    INTEGER,
    karma_modifier        INTEGER DEFAULT 5,
    karma_max             INTEGER,
    karma_current         INTEGER,
    gold                  INTEGER,
    silver                INTEGER,
    copper                INTEGER,
    current_damage        INTEGER,
    wounds                INTEGER,
    notes                 VARCHAR(4000)
);

CREATE TABLE character_talents (
    id                   BIGSERIAL PRIMARY KEY,
    character_id         BIGINT NOT NULL REFERENCES characters(id),
    talent_definition_id BIGINT NOT NULL REFERENCES talent_definitions(id),
    rank                 INTEGER
);

CREATE TABLE character_skills (
    id                  BIGSERIAL PRIMARY KEY,
    character_id        BIGINT NOT NULL REFERENCES characters(id),
    skill_definition_id BIGINT NOT NULL REFERENCES skill_definitions(id),
    rank                INTEGER
);

CREATE TABLE character_equipment (
    id                    BIGSERIAL PRIMARY KEY,
    character_id          BIGINT NOT NULL REFERENCES characters(id),
    name                  VARCHAR(255) NOT NULL,
    type                  VARCHAR(50) NOT NULL,
    description           VARCHAR(500),
    damage_bonus          INTEGER DEFAULT 0,
    physical_armor        INTEGER DEFAULT 0,
    mystical_armor        INTEGER DEFAULT 0,
    initiative_penalty    INTEGER DEFAULT 0,
    physical_defense_bonus INTEGER DEFAULT 0,
    mystic_defense_bonus  INTEGER DEFAULT 0,
    quantity              INTEGER DEFAULT 1,
    heal_step             INTEGER DEFAULT 0
);

CREATE TABLE character_spells (
    id                  BIGSERIAL PRIMARY KEY,
    character_id        BIGINT REFERENCES characters(id),
    spell_definition_id BIGINT REFERENCES spell_definitions(id)
);

CREATE TABLE combat_sessions (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    round      INTEGER DEFAULT 0,
    status     VARCHAR(50),
    phase      VARCHAR(20) DEFAULT 'ACTION',
    created_at TIMESTAMP
);

CREATE TABLE combatant_states (
    id                      BIGSERIAL PRIMARY KEY,
    combat_session_id       BIGINT REFERENCES combat_sessions(id),
    character_id            BIGINT REFERENCES characters(id),
    initiative              INTEGER,
    initiative_order        INTEGER,
    current_damage          INTEGER,
    wounds                  INTEGER,
    current_karma           INTEGER,
    defeated                BOOLEAN DEFAULT FALSE,
    is_npc                  BOOLEAN DEFAULT FALSE,
    has_acted_this_round    BOOLEAN DEFAULT FALSE,
    pending_attack_bonus    INTEGER DEFAULT 0,
    pending_defense_bonus   INTEGER DEFAULT 0,
    pending_dodge_damage    INTEGER DEFAULT 0,
    pending_dodge_attack_total INTEGER DEFAULT 0,
    knocked_down            BOOLEAN DEFAULT FALSE,
    pending_damage_step     INTEGER DEFAULT 0,
    pending_armor_value     INTEGER DEFAULT 0,
    pending_damage_roll_json TEXT,
    preparing_spell_id      BIGINT,
    threads_woven           INTEGER DEFAULT 0,
    threads_required        INTEGER DEFAULT 0,
    has_declared            BOOLEAN DEFAULT FALSE,
    declared_stance         VARCHAR(20) DEFAULT 'NONE',
    declared_action_type    VARCHAR(20) DEFAULT 'WEAPON'
);

CREATE TABLE active_effects (
    id                 BIGSERIAL PRIMARY KEY,
    combatant_state_id BIGINT REFERENCES combatant_states(id),
    name               VARCHAR(255) NOT NULL,
    description        VARCHAR(500),
    source_type        VARCHAR(50),
    source_id          BIGINT,
    remaining_rounds   INTEGER DEFAULT -1,
    negative           BOOLEAN DEFAULT FALSE
);

CREATE TABLE active_effect_modifiers (
    effect_id       BIGINT NOT NULL REFERENCES active_effects(id),
    target_stat     VARCHAR(50),
    mod_operation   VARCHAR(50),
    mod_value       DOUBLE PRECISION,
    trigger_context VARCHAR(50),
    mod_description VARCHAR(255)
);

CREATE TABLE combat_logs (
    id                BIGSERIAL PRIMARY KEY,
    combat_session_id BIGINT REFERENCES combat_sessions(id),
    round             INTEGER,
    logged_at         TIMESTAMP,
    action_type       VARCHAR(50),
    actor_name        VARCHAR(255),
    target_name       VARCHAR(255),
    description       VARCHAR(2000),
    roll_details_json VARCHAR(4000),
    success           BOOLEAN
);
