package com.earthdawn.service;

import com.earthdawn.model.DisciplineDefinition;
import com.earthdawn.model.GameCharacter;
import com.earthdawn.model.SkillDefinition;
import com.earthdawn.model.TalentDefinition;
import com.earthdawn.model.enums.AttributeType;
import com.earthdawn.model.enums.FreeActionTarget;
import com.earthdawn.model.enums.StatType;
import com.earthdawn.model.enums.TriggerContext;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.DisciplineRepository;
import com.earthdawn.repository.SkillDefinitionRepository;
import com.earthdawn.repository.TalentDefinitionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Befüllt die Referenzdaten beim ersten Start (Disziplinen, Talente, Fertigkeiten).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final DisciplineRepository disciplineRepo;
    private final TalentDefinitionRepository talentRepo;
    private final SkillDefinitionRepository skillRepo;
    private final CharacterRepository characterRepo;
    private final CharacterService characterService;
    private final EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        if (disciplineRepo.count() > 0) {
            log.info("Referenzdaten bereits vorhanden, überspringe Initialisierung.");
            migrateAttackTalents();
            migrateKarmaModifier();
            migrateFreeActionTalents();
            migrateDodgeTalent();
            migrateDisciplineBonuses();
            migrateActionTypeConstraint();
            return;
        }
        log.info("Initialisiere Earthdawn Referenzdaten...");
        seedTalents();
        seedSkills();
        seedDisciplines();
        log.info("Referenzdaten erfolgreich initialisiert.");
    }

    /**
     * Idempotente Migration: benennt alte Angriffstalente um, fügt Wurfwaffen hinzu,
     * setzt isAttackTalent-Flag und aktualisiert Disziplin-Referenzen.
     */
    private void migrateFreeActionTalents() {
        if (talentRepo.findByName("Magische Markierung").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Magische Markierung")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Markiert ein Ziel magisch. +2 auf eigenen nächsten Projektilwaffenangriff pro Übererfolg. Kostet 1 Schaden.")
                    .freeAction(true)
                    .freeActionTestStat(StatType.SPELL_DEFENSE)
                    .freeActionEffectTarget(FreeActionTarget.SELF)
                    .freeActionModifyStat(StatType.ATTACK_STEP)
                    .freeActionTriggerContext(TriggerContext.ON_RANGED_ATTACK)
                    .freeActionValuePerSuccess(2.0)
                    .freeActionDuration(1)
                    .freeActionDamageCost(1)
                    .build());
            log.info("Freie Aktion 'Magische Markierung' hinzugefügt.");
        }
    }

    private void migrateDodgeTalent() {
        if (talentRepo.findByName("Ausweichen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Ausweichen")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Weicht einem Angriff aus. Probe: Geschicklichkeit + Rang vs. Angriffswurf. Kostet 1 Schaden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Ausweichen' hinzugefügt.");
        }
    }

    private void migrateKarmaModifier() {
        for (GameCharacter c : characterRepo.findAll()) {
            if (c.getKarmaModifier() == 0) {
                c.setKarmaModifier(5);
            }
            c.setKarmaMax(c.getKarmaModifier() * c.getCircle());
            characterRepo.save(c);
        }
        log.info("Karma-Modifikator Migration abgeschlossen.");
    }

    private void migrateAttackTalents() {
        // Umbenennungen: alt → neu
        java.util.Map<String, String> renames = java.util.Map.of(
            "Kampfwaffen",  "Nahkampfwaffen",
            "Fernwaffen",   "Projektilwaffen",
            "Zauberspruch", "Spruchzauberei"
        );

        for (var entry : renames.entrySet()) {
            talentRepo.findByName(entry.getKey()).ifPresent(t -> {
                log.info("Talent umbenennen: {} → {}", entry.getKey(), entry.getValue());
                t.setName(entry.getValue());
                talentRepo.save(t);
                // Disziplin-Referenzen aktualisieren
                disciplineRepo.findAll().forEach(d -> {
                    if (d.getAccessTalentNames().remove(entry.getKey())) {
                        d.getAccessTalentNames().add(entry.getValue());
                        disciplineRepo.save(d);
                    }
                });
            });
        }

        // Wurfwaffen hinzufügen falls nicht vorhanden
        if (talentRepo.findByName("Wurfwaffen").isEmpty()) {
            log.info("Füge Wurfwaffen hinzu");
            talentRepo.save(talent("Wurfwaffen", AttributeType.DEXTERITY,
                "Wurfwaffen auf Gegner schleudern", true, true));
        }

        // isAttackTalent-Flag setzen
        java.util.Set<String> attackNames = java.util.Set.of(
            "Nahkampfwaffen", "Projektilwaffen", "Wurfwaffen", "Waffenloser Kampf", "Spruchzauberei"
        );
        talentRepo.findAll().forEach(t -> {
            boolean shouldBeAttack = attackNames.contains(t.getName());
            if (t.isAttackTalent() != shouldBeAttack) {
                t.setAttackTalent(shouldBeAttack);
                talentRepo.save(t);
            }
        });
    }

    private void seedTalents() {
        List<TalentDefinition> talents = List.of(
            talent("Nahkampfwaffen",    AttributeType.DEXTERITY,  "Angriffe mit Schwertern, Äxten oder Dolchen",                    true,  true),
            talent("Projektilwaffen",   AttributeType.DEXTERITY,  "Fernkampfangriffe mit Bögen, Armbrüsten oder Blasrohren",         true,  true),
            talent("Wurfwaffen",        AttributeType.DEXTERITY,  "Steine, Dolche, Speere oder andere Wurfwaffen schleudern",        true,  true),
            talent("Waffenloser Kampf", AttributeType.DEXTERITY,  "Angriffe mit Händen, Füßen oder anderen Körperteilen",           true,  true),
            talent("Spruchzauberei",    AttributeType.PERCEPTION, "Magische Angriffe gegen die Mystische Verteidigung eines Ziels", true,  true),
            talent("Ausweichen",        AttributeType.DEXTERITY,  "Verteidigung gegen Angriffe",                                    true,  false),
            talent("Initiative",        AttributeType.DEXTERITY,  "Initiative verbessern",                                          true,  false),
            talent("Lufttanz",          AttributeType.DEXTERITY,  "+Rang auf körperliche Verteidigung (passiv)",                    false, false),
            talent("Schlossknacken",    AttributeType.DEXTERITY,  "Schlösser öffnen",                                               true,  false),
            talent("Schleichen",        AttributeType.DEXTERITY,  "Leise bewegen",                                                  true,  false),
            talent("Klettern",          AttributeType.DEXTERITY,  "Klettern und Kracken",                                           true,  false),
            talent("Schwimmen",         AttributeType.STRENGTH,   "Schwimmen",                                                      true,  false),
            talent("Zähigkeit",         AttributeType.TOUGHNESS,  "+Rang auf Schadenstrack (passiv)",                               false, false),
            talent("Arkane Waffe",      AttributeType.PERCEPTION, "Magieangriff mit Waffe",                                         true,  false),
            talent("Fadenmagie",        AttributeType.PERCEPTION, "Magische Fäden weben",                                           true,  false),
            talent("Erste Hilfe",       AttributeType.PERCEPTION, "Wunden behandeln",                                               true,  false),
            talent("Tiergespür",        AttributeType.PERCEPTION, "Tiere beruhigen",                                                true,  false),
            talent("Überzeugung",       AttributeType.CHARISMA,   "NPC überzeugen",                                                 true,  false),
            talent("Einschüchterung",   AttributeType.CHARISMA,   "Gegner einschüchtern",                                           true,  false),
            talent("Meditation",        AttributeType.WILLPOWER,  "Karma regenerieren",                                             true,  false),
            talent("Standhalten",       AttributeType.WILLPOWER,  "Geistige Angriffe abwehren",                                     true,  false),
            talent("Wissensmagie",      AttributeType.WILLPOWER,  "Zauber mit Wissen verknüpfen",                                   true,  false)
        );
        talentRepo.saveAll(talents);
    }

    private void seedSkills() {
        List<SkillDefinition> skills = List.of(
            skill("Reiten",        AttributeType.DEXTERITY,  "Tiere reiten",           "Bewegung"),
            skill("Kartenkunde",   AttributeType.PERCEPTION, "Karten lesen",           "Wissen"),
            skill("Geschichte",    AttributeType.PERCEPTION, "Historisches Wissen",    "Wissen"),
            skill("Naturkunde",    AttributeType.PERCEPTION, "Pflanzen/Tiere kennen",  "Wissen"),
            skill("Alchimie",      AttributeType.PERCEPTION, "Tränke herstellen",      "Handwerk"),
            skill("Schmieden",     AttributeType.STRENGTH,   "Metallarbeit",           "Handwerk"),
            skill("Kochen",        AttributeType.PERCEPTION, "Speisen zubereiten",     "Handwerk"),
            skill("Handel",        AttributeType.CHARISMA,   "Preise verhandeln",      "Sozial"),
            skill("Etikette",      AttributeType.CHARISMA,   "Höfisches Verhalten",    "Sozial"),
            skill("Straßenkunde",  AttributeType.PERCEPTION, "Stadtleben kennen",      "Wissen"),
            skill("Magierkunde",   AttributeType.PERCEPTION, "Magie und Sprüche",      "Wissen"),
            skill("Theologie",     AttributeType.PERCEPTION, "Religion und Götter",    "Wissen")
        );
        skillRepo.saveAll(skills);
    }

    private void seedDisciplines() {
        List<DisciplineDefinition> disciplines = List.of(
            discipline("Krieger", 8,
                "Meister des direkten Kampfes, zäh und stark.",
                List.of("Nahkampfwaffen", "Ausweichen", "Zähigkeit", "Initiative", "Standhalten")),

            discipline("Pfadsucher", 6,
                "Kundschafter und Überlebenskünstler der Wildnis.",
                List.of("Projektilwaffen", "Schleichen", "Klettern", "Tiergespür", "Initiative")),

            discipline("Dieb", 6,
                "Geschickter Fingerakrobat und Meister der Heimlichkeit.",
                List.of("Schlossknacken", "Schleichen", "Ausweichen", "Waffenloser Kampf", "Initiative")),

            discipline("Elementarist", 6,
                "Magieanwender der vier klassischen Elemente.",
                List.of("Spruchzauberei", "Fadenmagie", "Wissensmagie", "Meditation", "Überzeugung")),

            discipline("Nekromant", 6,
                "Meister der Untotmagie und der Astralwelt.",
                List.of("Spruchzauberei", "Fadenmagie", "Wissensmagie", "Standhalten", "Meditation")),

            discipline("Illusionist", 6,
                "Meister der Trugbilder und Verblendung.",
                List.of("Spruchzauberei", "Fadenmagie", "Überzeugung", "Einschüchterung", "Meditation")),

            discipline("Schwertkämpfer", 8,
                "Eleganter Krieger, vereint Kampf und Magie.",
                List.of("Nahkampfwaffen", "Lufttanz", "Ausweichen", "Arkane Waffe", "Initiative")),

            discipline("Troubadour", 6,
                "Geschichtenerzähler und sozialer Meister.",
                List.of("Überzeugung", "Einschüchterung", "Erste Hilfe", "Meditation", "Initiative"))
        );
        disciplineRepo.saveAll(disciplines);
    }

    private TalentDefinition talent(String name, AttributeType attr, String desc, boolean testable, boolean attackTalent) {
        return TalentDefinition.builder()
                .name(name)
                .attribute(attr)
                .description(desc)
                .testable(testable)
                .attackTalent(attackTalent)
                .build();
    }

    private SkillDefinition skill(String name, AttributeType attr, String desc, String category) {
        return SkillDefinition.builder()
                .name(name)
                .attribute(attr)
                .description(desc)
                .category(category)
                .build();
    }

    private void migrateDisciplineBonuses() {
        // Renames and bonus assignment based on official ED4 table
        record DM(String oldName, String newName, int bw, int td) {}
        List<DM> migrations = List.of(
            new DM("Krieger",       "Krieger",       7, 8),
            new DM("Schwertkämpfer","Schwertmeister", 7, 8),
            new DM("Pfadsucher",    "Kundschafter",  5, 6),
            new DM("Dieb",          "Dieb",          5, 6),
            new DM("Troubadour",    "Troubadour",    5, 6),
            new DM("Elementarist",  "Elementarist",  3, 4),
            new DM("Illusionist",   "Illusionist",   3, 4),
            new DM("Nekromant",     "Magier",        3, 4)
        );
        for (DM m : migrations) {
            disciplineRepo.findByName(m.oldName()).ifPresent(d -> {
                d.setName(m.newName());
                d.setBwBonusPerCircle(m.bw());
                d.setTdBonusPerCircle(m.td());
                disciplineRepo.save(d);
            });
        }
        log.info("Disziplin-Boni migriert.");
    }

    private DisciplineDefinition discipline(String name, int karmaStep, String desc, List<String> talents) {
        return DisciplineDefinition.builder()
                .name(name)
                .karmaStep(karmaStep)
                .description(desc)
                .accessTalentNames(talents)
                .build();
    }

    /**
     * Drops and recreates the check constraint on combat_logs.action_type
     * to include newly added ActionType enum values.
     */
    private void migrateActionTypeConstraint() {
        try {
            entityManager.createNativeQuery(
                "ALTER TABLE combat_logs DROP CONSTRAINT IF EXISTS combat_logs_action_type_check"
            ).executeUpdate();
            entityManager.createNativeQuery(
                "ALTER TABLE combat_logs ADD CONSTRAINT combat_logs_action_type_check " +
                "CHECK (action_type IN ('MELEE_ATTACK','RANGED_ATTACK','SPELL_ATTACK'," +
                "'TALENT_TEST','SKILL_TEST','RECOVERY_TEST','INITIATIVE'," +
                "'EFFECT_ADDED','EFFECT_REMOVED','VALUE_CHANGED','ROUND_CHANGE'," +
                "'COMBAT_OPTION','FREE_ACTION','DODGE','STAND_UP','AUFSPRINGEN'))"
            ).executeUpdate();
            log.info("action_type CHECK-Constraint aktualisiert.");
        } catch (Exception e) {
            log.warn("Konnte action_type CHECK-Constraint nicht aktualisieren: {}", e.getMessage());
        }
    }
}
