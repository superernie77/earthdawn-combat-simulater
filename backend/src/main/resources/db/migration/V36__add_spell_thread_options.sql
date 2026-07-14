-- Zusatzfäden bei Zaubern (Stufe 1: nur EFFECT_STEP wird verrechnet, Rest ist Anzeige)

CREATE TABLE spell_thread_options (
    spell_id     BIGINT       NOT NULL REFERENCES spell_definitions (id) ON DELETE CASCADE,
    option_order INTEGER      NOT NULL,
    label        VARCHAR(200),
    option_type  VARCHAR(30),
    option_value INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (spell_id, option_order)
);

-- Gewählte Optionen des gerade vorbereiteten Zaubers (CSV der Indizes in spell_thread_options)
ALTER TABLE combatant_states ADD COLUMN extra_thread_choices VARCHAR(200);
