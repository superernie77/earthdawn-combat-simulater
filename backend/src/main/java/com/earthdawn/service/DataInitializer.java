package com.earthdawn.service;

import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.*;
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
    private final SpellDefinitionRepository spellRepo;
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
            migrateFadenwebenTalents();
            migrateGeisterbeschwoererDiscipline();
            seedSpells();
            migrateIllusionistSpells();
            migrateGeisterbeschwoererSpells();
            cleanupUnimplementedTalents();
            migrateExtraSuccessEffects();
            return;
        }
        log.info("Initialisiere Earthdawn Referenzdaten...");
        seedTalents();
        seedSkills();
        seedDisciplines();
        migrateFadenwebenTalents();
        migrateGeisterbeschwoererDiscipline();
        seedSpells();
        migrateIllusionistSpells();
        migrateGeisterbeschwoererSpells();
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

        if (talentRepo.findByName("Standhaftigkeit").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Standhaftigkeit")
                    .attribute(AttributeType.STRENGTH)
                    .description("Verbessert die Niederschlagsprobe. Bei einem Treffer, der eine Wunde verursacht, " +
                            "wird STR-Stufe + Talentrang statt der reinen STR-Stufe gegen (Schaden − Wundschwelle) gewürfelt. " +
                            "Freie Aktion, keine Kosten.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Standhaftigkeit' hinzugefügt.");
        }

        if (talentRepo.findByName("Verspotten").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Verspotten")
                    .attribute(AttributeType.CHARISMA)
                    .description("Beleidigt und demütigt einen Gegner (CHA + Rang vs. Soziale Verteidigung des Ziels). " +
                            "Hauptaktion, kostet 1 Überanstrengung. " +
                            "Erfolg: −1 pro Übererfolg auf alle Proben und Soziale Verteidigung des Ziels für Rang Runden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Verspotten' hinzugefügt.");
        }

        if (talentRepo.findByName("Starrsinn").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Starrsinn")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Gegenprobe gegen Verspotten (STU + Rang vs. Verspotten-Ergebnis). " +
                            "Gelingt die Probe, wird der Effekt des Verspottens vollständig negiert.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Starrsinn' hinzugefügt.");
        }

        if (talentRepo.findByName("Akrobatische Verteidigung").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Akrobatische Verteidigung")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Akrobatische Manöver im Kampf (GES + Rang vs. höchste KV der Gegner). " +
                            "Einfache Aktion, kostet 1 Überanstrengung. " +
                            "Erfolg: +2 KV pro Erfolg bis Rundenende. Bonus erlischt sofort bei Niedergeschlagen. " +
                            "Kann nicht mit Kampfsinn kombiniert werden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Akrobatische Verteidigung' hinzugefügt.");
        }

        if (talentRepo.findByName("Kampfsinn").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Kampfsinn")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Gegnerische Angriffe intuitiv vorhersehen (WAH + Rang vs. MV des Ziels). " +
                            "Einfache Aktion, kostet 1 Überanstrengung. " +
                            "Nur gegen Gegner mit niedrigerer Initiative. " +
                            "Erfolg: +2 KV und +2 auf nächsten Angriff pro Erfolg bis Rundenende. " +
                            "Kann nicht mit Akrobatischer Verteidigung kombiniert werden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Kampfsinn' hinzugefügt.");
        }

        if (talentRepo.findByName("Ablenken").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Ablenken")
                    .attribute(AttributeType.CHARISMA)
                    .description("Lenkt einen Gegner ab (CHA + Rang vs. Soziale Verteidigung). " +
                            "Einfache Aktion, kostet 1 Überanstrengung. " +
                            "Erfolg: −1 KV pro Erfolg für Anwender (Toter Winkel rückwärts) " +
                            "und −1 KV pro Erfolg für Ziel (Toter Winkel für Verbündete) bis Rundenende.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Ablenken' hinzugefügt.");
        }

        if (talentRepo.findByName("Eiserner Wille").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Eiserner Wille")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Widerstand gegen magische Angriffe (WIL + Rang vs. Angriffswurf des Zauberers). " +
                            "Freie Aktion, kostet 1 Überanstrengung. " +
                            "Bei Erfolg: aktiver magischer Effekt wird abgewehrt.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Eiserner Wille' hinzugefügt.");
        }
    }

    /**
     * Idempotente Migration: setzt extraSuccessEffect für alle Zauber gemäß Spreadsheet.
     * DAMAGE  = +2 Schadensstufe pro Übererfolg
     * DURATION = Dauer verlängert sich (wird nur im Log angezeigt)
     * TARGET   = zusätzliches Ziel (nicht mechanisch umgesetzt)
     * NONE     = kein Effekt
     */
    private void migrateExtraSuccessEffects() {
        // Schadenszauber MIT Schaden+2 pro Übererfolg
        java.util.List<String> damageExtra = java.util.List.of(
            "Blitz", "Phantomflamme", "Phantomfeuerball",
            "Echte Geschosse", "Illusionäre Geschosse", "Vorgezeichneter Weg",
            // Elementarist / Geisterbeschwörer — direkte Schadenszauber
            "Flammenpfeil", "Eisnadeln", "Feuerball", "Erdbeben",
            "Geisterdolch", "Geisterpfeil", "Lebensraub", "Todeshauch",
            "Astralspeer", "Knochensplitter", "Astralmaul", "Knochenbrechung",
            "Astralfeuer", "Astralsturm", "Gewichtslosigkeit"
        );
        // Schadenszauber mit Dauer-Übererfolg (kein Damage-Bonus)
        java.util.List<String> durationExtra = java.util.List.of(
            "Illusionärer Blitz", "Ersticken", "Suggestive Stimme",
            "Tanzender Drache", "Gedächtnisnotiz", "Band der Verschwiegenheit",
            "Halt, Stehenbleiben", "Gedankennebel", "Rebellische Gliedmaße",
            "Astralkettenblitz", "Geistersturm", "Astrallanze"
        );
        // Schadenszauber mit Zusätzliches-Ziel-Übererfolg
        java.util.List<String> targetExtra = java.util.List.of("Phantomblitzschlag");

        for (String name : damageExtra) {
            spellRepo.findAll().stream()
                .filter(s -> s.getName().equals(name) && !"DAMAGE".equals(s.getExtraSuccessEffect()))
                .forEach(s -> { s.setExtraSuccessEffect("DAMAGE"); spellRepo.save(s); });
        }
        for (String name : durationExtra) {
            spellRepo.findAll().stream()
                .filter(s -> s.getName().equals(name) && !"DURATION".equals(s.getExtraSuccessEffect()))
                .forEach(s -> { s.setExtraSuccessEffect("DURATION"); spellRepo.save(s); });
        }
        for (String name : targetExtra) {
            spellRepo.findAll().stream()
                .filter(s -> s.getName().equals(name) && !"TARGET".equals(s.getExtraSuccessEffect()))
                .forEach(s -> { s.setExtraSuccessEffect("TARGET"); spellRepo.save(s); });
        }
        log.info("extraSuccessEffect für Zauber migriert.");
    }

    /**
     * Idempotente Migration: entfernt alle nicht-implementierten Talente aus der DB
     * und bereinigt die Disziplin-Zugriffslisten entsprechend.
     */
    private void cleanupUnimplementedTalents() {
        java.util.List<String> toRemove = java.util.List.of(
            "Initiative", "Lufttanz", "Schlossknacken", "Schleichen", "Klettern",
            "Schwimmen", "Zähigkeit", "Arkane Waffe", "Fadenmagie", "Erste Hilfe",
            "Tiergespür", "Überzeugung", "Einschüchterung", "Meditation",
            "Standhalten", "Wissensmagie"
        );

        for (String name : toRemove) {
            talentRepo.findByName(name).ifPresent(talent -> {
                Long id = talent.getId();
                // Alles nativ löschen — Hibernate-Cascade komplett umgehen
                entityManager.createNativeQuery(
                    "DELETE FROM character_talents WHERE talent_definition_id = :id")
                    .setParameter("id", id).executeUpdate();
                entityManager.createNativeQuery(
                    "DELETE FROM talent_definitions WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
                entityManager.flush();
                entityManager.clear();
                log.info("Nicht-implementiertes Talent '{}' entfernt.", name);
            });

            // Aus Disziplin-Zugriffslisten entfernen
            disciplineRepo.findAll().forEach(d -> {
                if (d.getAccessTalentNames().remove(name)) {
                    disciplineRepo.save(d);
                }
            });
        }

        // Disziplin-Zugriffslisten auf implementierte Talente setzen
        migrateDisciplineAccessLists();
    }

    private void migrateDisciplineAccessLists() {
        // Hinweis: Disziplin-Namen wie sie nach migrateDisciplineBonuses() in der DB stehen
        java.util.Map<String, java.util.List<String>> accessMap = new java.util.HashMap<>();
        accessMap.put("Krieger",        java.util.List.of("Nahkampfwaffen", "Ausweichen", "Standhaftigkeit", "Verspotten", "Kampfsinn", "Akrobatische Verteidigung"));
        accessMap.put("Kundschafter",   java.util.List.of("Projektilwaffen", "Wurfwaffen", "Ausweichen", "Magische Markierung"));
        accessMap.put("Dieb",           java.util.List.of("Nahkampfwaffen", "Waffenloser Kampf", "Ausweichen", "Akrobatische Verteidigung", "Ablenken"));
        accessMap.put("Elementarist",   java.util.List.of("Spruchzauberei", "Elementarismus", "Eiserner Wille", "Standhaftigkeit"));
        accessMap.put("Magier",         java.util.List.of("Spruchzauberei", "Magie", "Eiserner Wille", "Standhaftigkeit", "Starrsinn"));
        accessMap.put("Illusionist",    java.util.List.of("Spruchzauberei", "Illusionismus", "Eiserner Wille", "Verspotten", "Ablenken"));
        accessMap.put("Schwertmeister", java.util.List.of("Nahkampfwaffen", "Ausweichen", "Kampfsinn", "Akrobatische Verteidigung", "Standhaftigkeit"));
        accessMap.put("Troubadour",     java.util.List.of("Verspotten", "Ablenken", "Ausweichen", "Magische Markierung"));
        accessMap.put("Geisterbeschwörer", java.util.List.of("Spruchzauberei", "Geisterbeschwörung", "Eiserner Wille", "Standhaftigkeit", "Starrsinn"));
        accessMap.put("Bogenschütze",   java.util.List.of("Projektilwaffen", "Wurfwaffen", "Ausweichen", "Magische Markierung"));
        accessMap.put("Waffenmeister",  java.util.List.of("Nahkampfwaffen", "Waffenloser Kampf", "Ausweichen", "Standhaftigkeit", "Kampfsinn"));

        disciplineRepo.findAll().forEach(d -> {
            java.util.List<String> newList = accessMap.get(d.getName());
            if (newList != null) {
                d.setAccessTalentNames(new java.util.ArrayList<>(newList));
                disciplineRepo.save(d);
                log.info("Disziplin '{}' Zugriffsliste aktualisiert.", d.getName());
            }
        });
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
        // Only seed talents that are actually implemented in combat.
        // Additional talents (weaving, free actions, social/defensive) are added via migrations.
        List<TalentDefinition> talents = List.of(
            talent("Nahkampfwaffen",    AttributeType.DEXTERITY,  "Angriffe mit Schwertern, Äxten oder Dolchen",                        true, true),
            talent("Projektilwaffen",   AttributeType.DEXTERITY,  "Fernkampfangriffe mit Bögen, Armbrüsten oder Blasrohren",             true, true),
            talent("Wurfwaffen",        AttributeType.DEXTERITY,  "Steine, Dolche, Speere oder andere Wurfwaffen schleudern",            true, true),
            talent("Waffenloser Kampf", AttributeType.DEXTERITY,  "Angriffe mit Händen, Füßen oder anderen Körperteilen",               true, true),
            talent("Spruchzauberei",    AttributeType.PERCEPTION, "Magische Angriffe gegen die MV (Mystische Verteidigung) eines Ziels", true, true),
            talent("Ausweichen",        AttributeType.DEXTERITY,  "Ausweichen nach einem Treffer (kostet 1 Schaden)",                    true, false)
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
                List.of("Nahkampfwaffen", "Ausweichen", "Standhaftigkeit", "Verspotten", "Kampfsinn", "Akrobatische Verteidigung")),

            discipline("Pfadsucher", 6,
                "Kundschafter und Überlebenskünstler der Wildnis.",
                List.of("Projektilwaffen", "Wurfwaffen", "Ausweichen", "Magische Markierung")),

            discipline("Dieb", 6,
                "Geschickter Fingerakrobat und Meister der Heimlichkeit.",
                List.of("Nahkampfwaffen", "Waffenloser Kampf", "Ausweichen", "Akrobatische Verteidigung", "Ablenken")),

            discipline("Elementarist", 6,
                "Magieanwender der vier klassischen Elemente.",
                List.of("Spruchzauberei", "Elementarismus", "Eiserner Wille", "Standhaftigkeit")),

            discipline("Nekromant", 6,
                "Meister der Untotmagie und der Astralwelt.",
                List.of("Spruchzauberei", "Eiserner Wille", "Standhaftigkeit", "Starrsinn")),

            discipline("Illusionist", 6,
                "Meister der Trugbilder und Verblendung.",
                List.of("Spruchzauberei", "Illusionismus", "Eiserner Wille", "Verspotten", "Ablenken")),

            discipline("Schwertkämpfer", 8,
                "Eleganter Krieger, vereint Kampf und Magie.",
                List.of("Nahkampfwaffen", "Ausweichen", "Kampfsinn", "Akrobatische Verteidigung", "Standhaftigkeit")),

            discipline("Troubadour", 6,
                "Geschichtenerzähler und sozialer Meister.",
                List.of("Verspotten", "Ablenken", "Ausweichen", "Magische Markierung"))
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

    // --- Fadenweben-Talente pro Disziplin ---

    private void migrateFadenwebenTalents() {
        record FW(String name, String desc) {}
        List<FW> variants = List.of(
            new FW("Elementarismus",     "Fäden weben für Elementarist-Zauber"),
            new FW("Illusionismus",      "Fäden weben für Illusionisten-Zauber"),
            new FW("Magie",             "Fäden weben für Magier-Zauber"),
            new FW("Geisterbeschwörung", "Fäden weben für Geisterbeschwörer-Zauber")
        );
        for (FW fw : variants) {
            if (talentRepo.findByName(fw.name()).isEmpty()) {
                talentRepo.save(TalentDefinition.builder()
                        .name(fw.name())
                        .attribute(AttributeType.PERCEPTION)
                        .description(fw.desc())
                        .testable(true)
                        .attackTalent(false)
                        .build());
                log.info("Fadenweben-Talent '{}' hinzugefügt.", fw.name());
            }
        }
    }

    // --- Geisterbeschwörer-Disziplin ---

    private void migrateGeisterbeschwoererDiscipline() {
        if (disciplineRepo.findByName("Geisterbeschwörer").isEmpty()) {
            disciplineRepo.save(DisciplineDefinition.builder()
                    .name("Geisterbeschwörer")
                    .karmaStep(6)
                    .bwBonusPerCircle(3)
                    .tdBonusPerCircle(4)
                    .description("Meister der Geistermagie und der Verbindung zu den Toten.")
                    .accessTalentNames(new java.util.ArrayList<>(List.of(
                            "Spruchzauberei", "Fadenmagie", "Geisterbeschwörung", "Standhalten", "Meditation")))
                    .build());
            log.info("Disziplin 'Geisterbeschwörer' hinzugefügt.");
        }
    }

    // --- Zauber-Seed-Daten ---

    private void seedSpells() {
        if (spellRepo.count() > 0) {
            log.info("Zauber bereits vorhanden, überspringe Zauber-Seed.");
            return;
        }
        log.info("Seed Zauber...");

        // Elementarist
        spellRepo.saveAll(List.of(
            spell("Flammenpfeil", "Elementarist", 1, 0, 0, 0,
                    SpellEffectType.DAMAGE, 4, "Ein kleiner Feuerpfeil", "4 Schaden"),
            spell("Windschutz", "Elementarist", 1, 0, 0, 5,
                    SpellEffectType.BUFF, 0, "+2 KV (Körperliche Verteidigung)", "+2 KV",
                    StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 2, TriggerContext.ALWAYS, 2),
            spell("Eisnadeln", "Elementarist", 1, 1, 6, 0,
                    SpellEffectType.DAMAGE, 6, "Nadeln aus Eis treffen das Ziel", "6 Schaden"),
            spell("Feuerball", "Elementarist", 2, 2, 8, 0,
                    SpellEffectType.DAMAGE, 10, "Schleudert einen Feuerball auf das Ziel", "10 Schaden"),
            spell("Flammenrüstung", "Elementarist", 2, 2, 8, 6,
                    SpellEffectType.BUFF, 0, "+3 Mystische Rüstung für den Zauberer", "+3 MR",
                    StatType.MYSTIC_ARMOR, ModifierOperation.ADD, 3, TriggerContext.ALWAYS, 3),
            spell("Erdbeben", "Elementarist", 3, 3, 10, 0,
                    SpellEffectType.DAMAGE, 12, "Die Erde bebt unter dem Ziel", "12 Schaden")
        ));

        // Illusionist
        spellRepo.saveAll(List.of(
            spell("Geisterpfeil", "Illusionist", 1, 0, 0, 0,
                    SpellEffectType.DAMAGE, 4, "Ein geisterhafter Pfeil trifft das Ziel", "4 Schaden"),
            spell("Trugbild", "Illusionist", 2, 1, 7, 0,
                    SpellEffectType.DEBUFF, 0, "-2 auf Angriffsstufe des Ziels", "-2 Angriff",
                    StatType.ATTACK_STEP, ModifierOperation.ADD, -2, TriggerContext.ALWAYS, 2),
            spell("Nebelwand", "Illusionist", 2, 2, 8, 6,
                    SpellEffectType.BUFF, 0, "+3 KV für Verbündeten", "+3 KV",
                    StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 3, TriggerContext.ALWAYS, 3),
            spell("Phantomschmerz", "Illusionist", 3, 2, 9, 0,
                    SpellEffectType.DAMAGE, 8, "Illusionärer Schmerz, Schaden gegen MV", "8 Schaden")
        ));

        // Magier
        spellRepo.saveAll(List.of(
            spell("Astralpfeil", "Magier", 1, 0, 0, 0,
                    SpellEffectType.DAMAGE, 5, "Ein astraler Energiestrahl", "5 Schaden"),
            spell("Astraler Schild", "Magier", 1, 1, 6, 6,
                    SpellEffectType.BUFF, 0, "+2 MV (Mystische Verteidigung)", "+2 MV",
                    StatType.SPELL_DEFENSE, ModifierOperation.ADD, 2, TriggerContext.ALWAYS, 3),
            spell("Energielanze", "Magier", 2, 2, 8, 0,
                    SpellEffectType.DAMAGE, 9, "Ein gebündelter Energiestrahl", "9 Schaden"),
            spell("Schwächung", "Magier", 2, 1, 7, 0,
                    SpellEffectType.DEBUFF, 0, "-2 auf Schadensstufe des Ziels", "-2 Schaden",
                    StatType.DAMAGE_STEP, ModifierOperation.ADD, -2, TriggerContext.ALWAYS, 2),
            spell("Arkane Rüstung", "Magier", 3, 2, 9, 6,
                    SpellEffectType.BUFF, 0, "+4 Mystische Rüstung", "+4 MR",
                    StatType.MYSTIC_ARMOR, ModifierOperation.ADD, 4, TriggerContext.ALWAYS, 3)
        ));

        // Geisterbeschwörer
        spellRepo.saveAll(List.of(
            spell("Geisterpfeil", "Geisterbeschwörer", 1, 0, 0, 0,
                    SpellEffectType.DAMAGE, 5, "Ein Geschoss aus Geisterenergie", "5 Schaden"),
            spell("Seelenschild", "Geisterbeschwörer", 1, 1, 6, 6,
                    SpellEffectType.BUFF, 0, "+2 Mystische Rüstung", "+2 MR",
                    StatType.MYSTIC_ARMOR, ModifierOperation.ADD, 2, TriggerContext.ALWAYS, 3),
            spell("Todeshauch", "Geisterbeschwörer", 2, 2, 8, 0,
                    SpellEffectType.DAMAGE, 8, "Ein kalter Hauch der Unterwelt", "8 Schaden"),
            spell("Geisterfesseln", "Geisterbeschwörer", 2, 1, 7, 0,
                    SpellEffectType.DEBUFF, 0, "-2 auf Initiative des Ziels", "-2 Initiative",
                    StatType.INITIATIVE_STEP, ModifierOperation.ADD, -2, TriggerContext.ALWAYS, 2),
            spell("Seelenraub", "Geisterbeschwörer", 3, 3, 10, 0,
                    SpellEffectType.DAMAGE, 11, "Entreißt dem Ziel Lebensenergie", "11 Schaden")
        ));

        log.info("{} Zauber geseedet.", spellRepo.count());
    }

    /** Damage spell helper */
    private SpellDefinition spell(String name, String discipline, int circle, int threads,
                                   int weavingDiff, int castingDiff,
                                   SpellEffectType type, int effectStep,
                                   String desc, String effectDesc) {
        return SpellDefinition.builder()
                .name(name).discipline(discipline).circle(circle)
                .threads(threads).weavingDifficulty(weavingDiff).castingDifficulty(castingDiff)
                .effectType(type).effectStep(effectStep)
                .description(desc).effectDescription(effectDesc)
                .build();
    }

    /** Buff/Debuff spell helper */
    private SpellDefinition spell(String name, String discipline, int circle, int threads,
                                   int weavingDiff, int castingDiff,
                                   SpellEffectType type, int effectStep,
                                   String desc, String effectDesc,
                                   StatType stat, ModifierOperation op, double value,
                                   TriggerContext trigger, int duration) {
        return SpellDefinition.builder()
                .name(name).discipline(discipline).circle(circle)
                .threads(threads).weavingDifficulty(weavingDiff).castingDifficulty(castingDiff)
                .effectType(type).effectStep(effectStep)
                .modifyStat(stat).modifyOperation(op).modifyValue(value).modifyTrigger(trigger)
                .duration(duration)
                .description(desc).effectDescription(effectDesc)
                .build();
    }

    // -----------------------------------------------------------------------
    // Zauber aus Spreadsheet – Illusionist & Geisterbeschwörer
    // -----------------------------------------------------------------------

    private void saveSpellIfAbsent(SpellDefinition s) {
        if (spellRepo.findByNameAndDiscipline(s.getName(), s.getDiscipline()).isEmpty()) {
            spellRepo.save(s);
        }
    }

    private SpellDefinition spellPhys(String name, String discipline, int circle, int threads,
                                       int weavingDiff, int castingDiff, int effectStep,
                                       String desc, String effectDesc) {
        return SpellDefinition.builder()
                .name(name).discipline(discipline).circle(circle)
                .threads(threads).weavingDifficulty(weavingDiff).castingDifficulty(castingDiff)
                .effectType(SpellEffectType.DAMAGE).effectStep(effectStep)
                .useMysticArmor(false)
                .description(desc).effectDescription(effectDesc)
                .build();
    }

    private void migrateIllusionistSpells() {
        if (spellRepo.findByNameAndDiscipline("Beruhigende Berührung", "Illusionist").isPresent()) return;
        log.info("Migriere Illusionisten-Zauber...");

        // --- Kreis 1 ---
        saveSpellIfAbsent(spell("Beruhigende Berührung", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.BUFF, 0, "Schützt vor Furcht; +3 MV & SV", "+3 MV & SV",
                StatType.SPELL_DEFENSE, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Blitz", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.DAMAGE, 1, "WIL+1/Mystisch; -2 auf nächste Durchschauen-Probe", "WIL+1 Mystisch"));
        saveSpellIfAbsent(spell("Botschaft Senden", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.BUFF, 0, "Übermittelt kurze Papierbotschaft an sichtbares Ziel", "Botschaft senden"));
        saveSpellIfAbsent(spell("Falsches Gesicht", "Illusionist", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "Verändert Gesicht; +3 auf Charisma-Proben", "Gesicht verändern"));
        saveSpellIfAbsent(spell("Illusionärer Blitz", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.DAMAGE, 4, "WIL+4/Mystisch; Ziel -2 auf Willenskraftproben", "WIL+4 Mystisch"));
        saveSpellIfAbsent(spell("Katastrophe", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.BUFF, 0, "Illusionäre Ablenkung (Rauch, Schreie usw.)", "Ablenkung"));
        saveSpellIfAbsent(spell("Schreckgestalt", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.BUFF, 0, "+2 Nahkampf-Angriff & Schaden; +2 KV; Ziel wirkt monsterhaft", "+2 KV",
                StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Spaß mit Türen", "Illusionist", 1, 2, 5, 6,
                SpellEffectType.BUFF, 0, "Erschafft oder verändert Türillusionen", "Türillusion"));
        saveSpellIfAbsent(spell("Umhang", "Illusionist", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "Ziel wird unauffälliger; +3 auf Heimlichkeitsproben", "+3 Heimlichkeit"));
        saveSpellIfAbsent(spell("Unsichtbare Stimmen", "Illusionist", 1, 0, 5, 0,
                SpellEffectType.BUFF, 0, "Illusionäre Stimmen als Ablenkung oder Stimmenmimikry", "Stimmenillusion"));
        saveSpellIfAbsent(spell("Verschlüsseln", "Illusionist", 1, 0, 5, 6,
                SpellEffectType.BUFF, 0, "Text wird unlesbar; nur Zauberer versteht ihn", "Text verschlüsseln"));
        saveSpellIfAbsent(spell("Vertrauen", "Illusionist", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "Ziel vertraut dem Zauberer; wirkt freundlich (nach Ende: Haltung -1)", "-1 SV (Vertrauen)",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, -1.0, TriggerContext.ALWAYS, 3));

        // --- Kreis 2 ---
        saveSpellIfAbsent(spell("Abbild Versetzen", "Illusionist", 2, 1, 6, 0,
                SpellEffectType.BUFF, 0, "Projiziert Bild des Ziels; macht es unsichtbar", "Unsichtbar machen"));
        saveSpellIfAbsent(spell("Blindheit", "Illusionist", 2, 1, 6, 0,
                SpellEffectType.DEBUFF, 0, "Ziel sieht nur Schwärze; Blindheitsmalus auf alle Proben", "-3 Angriff (blind)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Gedankennebel", "Illusionist", 2, 1, 6, 0,
                SpellEffectType.DAMAGE, 3, "WIL+3/Mystisch; Ziel vergisst geplante Aktionen", "WIL+3 Mystisch"));
        saveSpellIfAbsent(spell("Harmloses Treiben", "Illusionist", 2, 1, 6, 0,
                SpellEffectType.BUFF, 0, "Maskiert Aktivität des Ziels als harmlos", "Aktivität tarnen"));
        saveSpellIfAbsent(spellPhys("Phantomflamme", "Illusionist", 2, 1, 6, 0,
                6, "WIL+6/Physisch (illusionäre Flamme)", "WIL+6 Physisch"));
        saveSpellIfAbsent(spell("Sehen von Verborgenem", "Illusionist", 2, 1, 6, 0,
                SpellEffectType.BUFF, 0, "+5 auf Sicht-Wahrnehmungsproben für verborgene Dinge", "+5 Wahrnehmung"));

        // --- Kreis 3 ---
        saveSpellIfAbsent(spell("Blendendes Licht", "Illusionist", 3, 1, 7, 0,
                SpellEffectType.DEBUFF, 0, "Alle im Bereich erleiden Dunkelheitsmalus (4 Schritt Radius)", "-3 Angriff (geblendet)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 2));
        saveSpellIfAbsent(spell("Nebel des Spotts", "Illusionist", 3, 1, 7, 0,
                SpellEffectType.DEBUFF, 0, "Erzürnt/Demütigt; erzwingt Aggressiven Angriff; Bedrängt (4 Schritt Radius)", "-2 Angriff (Spott)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Niemand Da", "Illusionist", 3, 1, 7, 0,
                SpellEffectType.BUFF, 0, "Macht Gruppe für Außenstehende unsichtbar (stationär; 4 Schritt Radius)", "Gruppeninvisibilität"));
        saveSpellIfAbsent(spell("Phantomkrieger", "Illusionist", 3, 1, 7, 0,
                SpellEffectType.BUFF, 0, "Erschafft 3 Abbilder; +3 KV; Gegner -3 auf Verteidigung", "+3 KV",
                StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Und ein Schleier fiel", "Illusionist", 3, 0, 7, 0,
                SpellEffectType.BUFF, 0, "+5 auf Durchschauen-Proben für 2 Runden", "+5 Durchschauen"));

        // --- Kreis 4 ---
        saveSpellIfAbsent(spell("Demaskieren", "Illusionist", 4, 0, 8, 0,
                SpellEffectType.BUFF, 0, "Enthüllt wahre Erscheinung; +5 auf Magie Neutralisieren", "Wahre Form enthüllen"));
        saveSpellIfAbsent(spell("Ersticken", "Illusionist", 4, 3, 8, 0,
                SpellEffectType.DAMAGE, 2, "WIL+2/Mystisch; Ziele ersticken; Bedrängt; halbe Bewegungsrate (4 Schritt Radius)", "WIL+2 Mystisch"));
        saveSpellIfAbsent(spell("Große Waffe", "Illusionist", 4, 0, 8, 0,
                SpellEffectType.DEBUFF, 0, "Waffe wirkt größer; Gegner Bedrängt; Verteidigung = Durchschauen-Wert", "-2 KV (Bedrängt)",
                StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, -2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Halt, Stehenbleiben", "Illusionist", 4, 1, 8, 0,
                SpellEffectType.DEBUFF, 0, "Immobilisiert Ziel; Bewegungsrate 0; Bedrängt", "-5 Initiative (gelähmt)",
                StatType.INITIATIVE_STEP, ModifierOperation.ADD, -5.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spellPhys("Phantomblitzschlag", "Illusionist", 4, 1, 8, 0,
                7, "WIL+7/Physisch; Blitzschlag-Illusion", "WIL+7 Physisch"));
        saveSpellIfAbsent(spell("Suggestive Stimme", "Illusionist", 4, 3, 8, 0,
                SpellEffectType.DAMAGE, 4, "WIL+4/Mystisch; erschafft überredende Stimme; zwingt zu Handlungen", "WIL+4 Mystisch"));
        saveSpellIfAbsent(spell("Unauffälligkeit", "Illusionist", 4, 1, 8, 0,
                SpellEffectType.BUFF, 0, "Ziel wird komplett ignoriert außer bei direkten Interaktionen", "In Menge verschwinden"));

        // --- Kreis 5 ---
        saveSpellIfAbsent(spell("Auge der Wahrheit", "Illusionist", 5, 1, 9, 0,
                SpellEffectType.BUFF, 0, "Magische Erkennung von Wahrheit und Lüge", "Wahrheit erkennen"));
        saveSpellIfAbsent(spell("Band der Verschwiegenheit", "Illusionist", 5, 2, 9, 0,
                SpellEffectType.DEBUFF, 0, "WIL+4/Mystisch; verbietet direktes Sprechen über ein Thema", "-3 SV (Verschwiegenheit)",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 5));
        saveSpellIfAbsent(spell("Illusion", "Illusionist", 5, 3, 9, 6,
                SpellEffectType.BUFF, 0, "Erschafft einfache Illusionen (10 Schritt Radius)", "Illusion erschaffen"));
        saveSpellIfAbsent(spellPhys("Phantomfeuerball", "Illusionist", 5, 1, 9, 0,
                5, "WIL+5/Physisch; teilweise Blindheit (4 Schritt Radius)", "WIL+5 Physisch"));
        saveSpellIfAbsent(spell("Presto!", "Illusionist", 5, 1, 9, 6,
                SpellEffectType.BUFF, 0, "Verbindet zwei kleine Öffnungen miteinander", "Öffnungen verbinden"));
        saveSpellIfAbsent(spell("Rollentausch", "Illusionist", 5, 3, 9, 0,
                SpellEffectType.BUFF, 0, "Tauscht Aussehen mit dem Ziel", "Aussehen tauschen"));

        // --- Kreis 6 ---
        saveSpellIfAbsent(spell("Astralschatten", "Illusionist", 6, 2, 10, 0,
                SpellEffectType.BUFF, 0, "Verbirgt das Ziel im Astralraum", "Astrales Verstecken"));
        saveSpellIfAbsent(spellPhys("Echte Geschosse", "Illusionist", 6, 2, 10, 0,
                4, "WIL+4/Physisch; -2 auf nächste Durchschauen-Probe (6 Schritt Radius)", "WIL+4 Physisch"));
        saveSpellIfAbsent(spell("Fliegender Teppich", "Illusionist", 6, 2, 10, 7,
                SpellEffectType.BUFF, 0, "Erschafft fliegenden Teppich (200 Pfund Tragkraft)", "Fliegender Teppich"));
        saveSpellIfAbsent(spell("Gedächtnisnotiz", "Illusionist", 6, 4, 10, 0,
                SpellEffectType.DEBUFF, 0, "WIL+6/Mystisch; verändert Erinnerungen des Ziels", "-3 SV (Gedächtnis)",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 5));
        saveSpellIfAbsent(spellPhys("Illusionäre Geschosse", "Illusionist", 6, 2, 10, 0,
                8, "WIL+8/Physisch (illusionäre Geschosse; 6 Schritt Radius)", "WIL+8 Physisch"));
        saveSpellIfAbsent(spell("Positionstausch", "Illusionist", 6, 3, 10, 0,
                SpellEffectType.BUFF, 0, "Tauscht die Position mit dem Ziel (real)", "Positionen tauschen"));
        saveSpellIfAbsent(spell("Vorgezeichneter Weg", "Illusionist", 6, 3, 10, 6,
                SpellEffectType.DAMAGE, 8, "WIL+8/Mystisch; zwingt andere den gewählten Weg zu nehmen (60 Schritt)", "WIL+8 Mystisch"));

        // --- Kreis 7 ---
        saveSpellIfAbsent(spell("Gebrabbel", "Illusionist", 7, 1, 11, 0,
                SpellEffectType.DEBUFF, 0, "Bringt Sprache des Ziels vollständig durcheinander", "-3 SV (Gebrabbel)",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Illusionäre Stampede", "Illusionist", 7, 2, 11, 0,
                SpellEffectType.DEBUFF, 0, "Erschafft Stampede-Illusion; Ziele Bedrängt (multiple Ziele)", "-2 KV (Stampede)",
                StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, -2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Lautlose Stampede", "Illusionist", 7, 4, 11, 0,
                SpellEffectType.BUFF, 0, "Unterdrückt Geräusche; +4 Heimlichkeit für die gesamte Gruppe", "+4 Heimlichkeit Gruppe"));
        saveSpellIfAbsent(spell("Schwindelgefühl", "Illusionist", 7, 1, 11, 0,
                SpellEffectType.DEBUFF, 0, "-2 auf alle Aktionsproben des Ziels pro Erfolg", "-2 Aktionen (Schwindel)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spellPhys("Tanzender Drache", "Illusionist", 7, 4, 11, 12,
                6, "Illusionärer Drache greift an (WIL+6/Physisch; 60 Schritt)", "WIL+6 Physisch (Drache)"));
        saveSpellIfAbsent(spell("Zeitweilige Öffnung", "Illusionist", 7, 1, 11, 0,
                SpellEffectType.BUFF, 0, "Öffnet temporär ein Hindernis bis 2 Schritt Dicke", "Hindernis öffnen"));

        // --- Kreis 8 ---
        saveSpellIfAbsent(spell("Dimensionstor", "Illusionist", 8, 3, 12, 0,
                SpellEffectType.BUFF, 0, "Verbindet zwei Portale innerhalb von 1 Meile", "Dimensionstor öffnen"));
        saveSpellIfAbsent(spell("Gesichtslos", "Illusionist", 8, 2, 12, 0,
                SpellEffectType.DEBUFF, 0, "Entfernt Gesichtszüge des Ziels; kann nicht sehen oder sprechen", "-5 SV (Gesichtslos)",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, -5.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Gestalttausch", "Illusionist", 8, 4, 12, 0,
                SpellEffectType.BUFF, 0, "Tauscht Position und Aussehen mit dem Ziel", "Gestalt tauschen"));
        saveSpellIfAbsent(spell("Gestank", "Illusionist", 8, 2, 12, 0,
                SpellEffectType.DEBUFF, 0, "Lähmender Gestank; Ziele würgen und erbrechen (4 Schritt Radius)", "-3 Angriff (Gestank)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Rebellische Gliedmaße", "Illusionist", 8, 1, 12, 0,
                SpellEffectType.DAMAGE, 4, "WIL+4/Mystisch; kontrolliert Gliedmaße des Ziels; widersteht jede Runde", "WIL+4 Mystisch"));
        saveSpellIfAbsent(spell("Zauber Überschatten", "Illusionist", 8, 2, 12, 0,
                SpellEffectType.DEBUFF, 0, "Schwächt Zauber des Ziels; Wirkungsprobe um Wirkungsstufe reduziert", "-3 Schaden (Zauberschatten)",
                StatType.DAMAGE_STEP, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));

        log.info("55 Illusionisten-Zauber migriert.");
    }

    private void migrateGeisterbeschwoererSpells() {
        if (spellRepo.findByNameAndDiscipline("Astralspeer", "Geisterbeschwörer").isPresent()) return;
        log.info("Migriere Geisterbeschwörer-Zauber...");

        // --- Kreis 1 ---
        saveSpellIfAbsent(spell("Astralspeer", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.DAMAGE, 4, "WIL+4/Mystisch; ätherischer Speer", "WIL+4 Mystisch"));
        saveSpellIfAbsent(spell("Ätherische Finsternis", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.DEBUFF, 0, "Magische Dunkelheit; Malus auf Sichtproben", "-2 Angriff (Dunkelheit)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Augenblick des Todes", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.DAMAGE, 3, "WIL+3/Mystisch; Zauberer erlebt Schaden des Verstorbenen; temporäre SP", "WIL+3 Mystisch"));
        saveSpellIfAbsent(spell("Dunkler Bote", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "Übermittelt Botschaft per nachtaktivem Flugtier", "Botschaft senden"));
        saveSpellIfAbsent(spell("Geisterhand", "Geisterbeschwörer", 1, 0, 5, 0,
                SpellEffectType.DAMAGE, 2, "WIL+2/Mystisch; -2 auf KV & MV des Ziels", "WIL+2 Mystisch"));
        saveSpellIfAbsent(spell("Geisterpfeil", "Geisterbeschwörer", 1, 0, 5, 0,
                SpellEffectType.DAMAGE, 2, "WIL+2/Mystisch; -2 auf Mystische Rüstung des Ziels", "WIL+2 Mystisch"));
        saveSpellIfAbsent(spell("Kleiner Bannkreis", "Geisterbeschwörer", 1, 1, 5, 6,
                SpellEffectType.BUFF, 0, "Schützt vor Untoten/Dämonen; verursacht Schaden bei ihnen (4 Schritt Radius)", "Bannkreis"));
        saveSpellIfAbsent(spell("Knochenkreis", "Geisterbeschwörer", 1, 3, 5, 0,
                SpellEffectType.BUFF, 0, "Beschwört Knochengeist in Berührungsradius", "Knochengeist beschwören"));
        saveSpellIfAbsent(spell("Schattenverschmelzung", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "+4 auf Heimlicher Schritt (Stealth)", "+4 Heimlichkeit"));
        saveSpellIfAbsent(spell("Seelenlose Augen", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "+3 auf Einschüchterungsproben", "+3 Einschüchtern"));
        saveSpellIfAbsent(spell("Seelenrüstung", "Geisterbeschwörer", 1, 1, 5, 0,
                SpellEffectType.BUFF, 0, "+3 auf Mystische Rüstung", "+3 MR",
                StatType.MYSTIC_ARMOR, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));

        // --- Kreis 2 ---
        saveSpellIfAbsent(spell("Aspekt des Nebelgeistes", "Geisterbeschwörer", 2, 1, 6, 0,
                SpellEffectType.BUFF, 0, "Bindet Nebelgeist; +3 Nahkampf Angriff & Schaden; +3 KV", "+3 KV",
                StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Kreis der Kälte", "Geisterbeschwörer", 2, 0, 6, 0,
                SpellEffectType.DAMAGE, 4, "WIL+4/Mystisch; Kälteschaden; Bewegungsrate halbiert (2 Schritt Radius)", "WIL+4 Mystisch (Kälte)"));
        saveSpellIfAbsent(spell("Nebelgeist Beschwören", "Geisterbeschwörer", 2, 1, 6, 0,
                SpellEffectType.BUFF, 0, "Beschwört Nebelgeist; greift Ziele an", "Nebelgeist beschwören"));
        saveSpellIfAbsent(spell("Nebelschild", "Geisterbeschwörer", 2, 0, 6, 0,
                SpellEffectType.BUFF, 0, "+4 auf Proben auf Hieb Ausweichen", "+4 Ausweichen",
                StatType.PHYSICAL_DEFENSE, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Schädel des Todes", "Geisterbeschwörer", 2, 0, 6, 0,
                SpellEffectType.BUFF, 0, "Verängstigen wird zur einfachen Aktion möglich", "Verängstigen verbessern"));
        saveSpellIfAbsent(spell("Schattengeflüster", "Geisterbeschwörer", 2, 1, 6, 0,
                SpellEffectType.BUFF, 0, "Lauschen über Schatten (bis 100 Schritt Reichweite)", "Durch Schatten lauschen"));
        saveSpellIfAbsent(spell("Schneide der Nacht", "Geisterbeschwörer", 2, 0, 6, 0,
                SpellEffectType.BUFF, 0, "+3 Kälteschaden auf Waffe; Ziel -2 MV", "+3 Schaden (Kälte)",
                StatType.DAMAGE_STEP, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));

        // --- Kreis 3 ---
        saveSpellIfAbsent(spell("Aspekt des Feigen Herumschleichens", "Geisterbeschwörer", 3, 3, 7, 0,
                SpellEffectType.BUFF, 0, "Gewährt überlegene Kundschafterfähigkeiten (mit Nebenwirkungen)", "Kundschafterfähigkeiten"));
        saveSpellIfAbsent(spell("Aspekt des Knochengeistes", "Geisterbeschwörer", 3, 1, 7, 0,
                SpellEffectType.BUFF, 0, "Bindet Knochengeist; +4 MV & Mystische Rüstung", "+4 MV",
                StatType.SPELL_DEFENSE, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Grabesbotschaft", "Geisterbeschwörer", 3, 4, 7, 6,
                SpellEffectType.BUFF, 0, "Sendet Nachricht an Namensgeber über Geister (bis 20 Meilen)", "Grabesbotschaft"));
        saveSpellIfAbsent(spell("Knochengeist Beschwören", "Geisterbeschwörer", 3, 1, 7, 0,
                SpellEffectType.BUFF, 0, "Beschwört Knochengeist; gehorcht Befehlen des Zauberers", "Knochengeist beschwören"));
        saveSpellIfAbsent(spell("Nebel der Angst", "Geisterbeschwörer", 3, 1, 7, 6,
                SpellEffectType.DEBUFF, 0, "Verängstigt mehrere Ziele (4 Schritt Radius)", "-3 SV (Angst)",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Pfeil der Nacht", "Geisterbeschwörer", 3, 0, 7, 6,
                SpellEffectType.BUFF, 0, "+6 auf Projektilschaden; -2 auf Mystische Rüstung des Ziels", "+6 Projektilschaden",
                StatType.DAMAGE_STEP, ModifierOperation.ADD, 6.0, TriggerContext.ON_RANGED_ATTACK, 2));
        saveSpellIfAbsent(spell("Schmerzen", "Geisterbeschwörer", 3, 0, 7, 0,
                SpellEffectType.DEBUFF, 0, "Fügt 3 temporäre Wunden zu; halbiert Bewegungsrate", "-3 Wundschwelle",
                StatType.WOUND_THRESHOLD, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));

        // --- Kreis 4 ---
        saveSpellIfAbsent(spell("Aspekt des Bedrohlichen Tyrannen", "Geisterbeschwörer", 4, 1, 8, 0,
                SpellEffectType.BUFF, 0, "Boni bei sozialen Interaktionen (mit Nebenwirkungen)", "Soziale Boni"));
        saveSpellIfAbsent(spell("Böser Blick", "Geisterbeschwörer", 4, 0, 8, 0,
                SpellEffectType.BUFF, 0, "Verstärkt Talent Verängstigen; +3 auf Einschüchterungsproben", "+3 Einschüchtern",
                StatType.SOCIAL_DEFENSE, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Dunkler Spion", "Geisterbeschwörer", 4, 1, 8, 0,
                SpellEffectType.BUFF, 0, "Sieht und hört durch ein verbundenes Tier", "Durch Tier beobachten"));
        saveSpellIfAbsent(spell("Letzte Chance", "Geisterbeschwörer", 4, 1, 8, 0,
                SpellEffectType.BUFF, 0, "+4 auf Erholungsprobe eines sterbenden Charakters", "+4 Erholungsprobe",
                StatType.RECOVERY_STEP, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 1));
        saveSpellIfAbsent(spell("Sichtfenster", "Geisterbeschwörer", 4, 2, 8, 6,
                SpellEffectType.BUFF, 0, "Durch physische Barrieren sehen", "Durch Wände sehen"));
        saveSpellIfAbsent(spell("Umhang des Nachtfliegers", "Geisterbeschwörer", 4, 2, 8, 0,
                SpellEffectType.BUFF, 0, "Verwandlung in nachtaktives Flugtier", "In Flugtier verwandeln"));

        // --- Kreis 5 ---
        saveSpellIfAbsent(spell("Aspekt des Grausamen Arztes", "Geisterbeschwörer", 5, 1, 9, 0,
                SpellEffectType.BUFF, 0, "Gewährt Erholungsproben (mit Nebenwirkungen)", "+3 Erholung",
                StatType.RECOVERY_STEP, ModifierOperation.ADD, 3.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Astraler Schutzkreis", "Geisterbeschwörer", 5, 2, 9, 0,
                SpellEffectType.BUFF, 0, "+4 auf Mystische Rüstung (längere Wirkung)", "+4 MR",
                StatType.MYSTIC_ARMOR, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 5));
        saveSpellIfAbsent(spell("Erblinden", "Geisterbeschwörer", 5, 0, 9, 0,
                SpellEffectType.DEBUFF, 0, "Ziel wird blind; schwere Abzüge auf alle Aktionen", "-4 Angriff (blind)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -4.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Staub zu Staub", "Geisterbeschwörer", 5, 0, 9, 0,
                SpellEffectType.DAMAGE, 8, "WIL+8/Mystisch; vernichtet Untote vollständig", "WIL+8 Mystisch"));
        saveSpellIfAbsent(spell("Verdorren", "Geisterbeschwörer", 5, 3, 9, 0,
                SpellEffectType.DAMAGE, 6, "WIL+6/Mystisch; Gliedmaßen des Ziels schrumpfen", "WIL+6 Mystisch"));
        saveSpellIfAbsent(spell("Verdorren Umkehren", "Geisterbeschwörer", 5, 3, 9, 0,
                SpellEffectType.BUFF, 0, "Heilt verdorrte Gliedmaße; kehrt Verdorren-Zauber um", "Verdorren heilen"));

        // --- Kreis 6 ---
        saveSpellIfAbsent(spell("Astralschlund", "Geisterbeschwörer", 6, 2, 10, 0,
                SpellEffectType.DAMAGE, 6, "Beschwört riesiges Astralmaul; greift Ziel an", "WIL+6 Mystisch"));
        saveSpellIfAbsent(spell("Durch den Schatten Treten", "Geisterbeschwörer", 6, 2, 10, 0,
                SpellEffectType.BUFF, 0, "Bewegung durch Astralraum zwischen Schatten", "Schattenbewegung"));
        saveSpellIfAbsent(spell("Erholung", "Geisterbeschwörer", 6, 1, 10, 0,
                SpellEffectType.BUFF, 0, "+5 auf Erholungsprobe des Ziels", "+5 Erholung",
                StatType.RECOVERY_STEP, ModifierOperation.ADD, 5.0, TriggerContext.ALWAYS, 1));
        saveSpellIfAbsent(spell("Freundliche Finsternis", "Geisterbeschwörer", 6, 2, 10, 0,
                SpellEffectType.BUFF, 0, "Magische Dunkelheit; +2 auf Aktionsproben für Verbündete", "+2 Angriff (Dunkelheit)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, 2.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Knochenbrecher", "Geisterbeschwörer", 6, 2, 10, 0,
                SpellEffectType.DAMAGE, 6, "WIL+6/Mystisch; bricht Knochen des Ziels", "WIL+6 Mystisch"));
        saveSpellIfAbsent(spell("Schwächende Düsternis", "Geisterbeschwörer", 6, 2, 10, 0,
                SpellEffectType.DEBUFF, 0, "Lebensentziehender Nebel; halbe Bewegungsrate; 1 Wunde pro Runde", "-3 Wundschwelle (Düsternis)",
                StatType.WOUND_THRESHOLD, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3));

        // --- Kreis 7 ---
        saveSpellIfAbsent(spell("Aspekt des Gelegenheitsmörders", "Geisterbeschwörer", 7, 1, 11, 0,
                SpellEffectType.BUFF, 0, "+5 auf Angriff & Schaden im Nahkampf gegen Überraschte/Niedergeschlagene", "+5 Angriff (Gelegenheit)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, 5.0, TriggerContext.ON_MELEE_ATTACK, 3));
        saveSpellIfAbsent(spell("Astrales Leuchtfeuer", "Geisterbeschwörer", 7, 3, 11, 0,
                SpellEffectType.BUFF, 0, "Leuchtfeuer im Astralraum; Dämonenmal-Risiko", "Astralfeuer"));
        saveSpellIfAbsent(spell("Herzbeklemmung", "Geisterbeschwörer", 7, 4, 11, 0,
                SpellEffectType.DAMAGE, 5, "WIL+5/Mystisch; lähmt Ziel; verursacht Schaden pro Runde", "WIL+5 Mystisch"));
        saveSpellIfAbsent(spell("Knochenpudding", "Geisterbeschwörer", 7, 4, 11, 0,
                SpellEffectType.DEBUFF, 0, "Verwandelt Knochen in Pudding; 3 Wunden; Bewegung stark eingeschränkt", "-4 Wundschwelle",
                StatType.WOUND_THRESHOLD, ModifierOperation.ADD, -4.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Lähmkreis", "Geisterbeschwörer", 7, 2, 11, 0,
                SpellEffectType.DEBUFF, 0, "WIL/Mystisch; verhindert Bewegung des Ziels vollständig", "-5 Initiative (gelähmt)",
                StatType.INITIATIVE_STEP, ModifierOperation.ADD, -5.0, TriggerContext.ALWAYS, 5));
        saveSpellIfAbsent(spell("Üble Dämpfe", "Geisterbeschwörer", 7, 2, 11, 0,
                SpellEffectType.DAMAGE, 5, "WIL+5/Mystisch; Astraldämpfe; Schaden pro Runde (Bereich)", "WIL+5 Mystisch"));

        // --- Kreis 8 ---
        saveSpellIfAbsent(spell("Aspekt des Astralen Gelehrten", "Geisterbeschwörer", 8, 1, 12, 0,
                SpellEffectType.BUFF, 0, "Uneingeschränkte Astralraumwahrnehmung; +4 MV & KV", "+4 MV",
                StatType.SPELL_DEFENSE, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 3));
        saveSpellIfAbsent(spell("Astralklinge", "Geisterbeschwörer", 8, 0, 12, 0,
                SpellEffectType.BUFF, 0, "Waffe kann Ziele im Astralraum verletzen; +4 Schaden", "+4 Schaden (Astralklinge)",
                StatType.DAMAGE_STEP, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 5));
        saveSpellIfAbsent(spell("Dahinsiechen", "Geisterbeschwörer", 8, 5, 12, 0,
                SpellEffectType.DAMAGE, 6, "WIL+6/Mystisch; Ziel verliert Gewicht; erleidet fortlaufend Schaden", "WIL+6 Mystisch"));
        saveSpellIfAbsent(spell("Dämonenruf", "Geisterbeschwörer", 8, 6, 12, 0,
                SpellEffectType.BUFF, 0, "Beschwört und bindet einen Dämon in einem Kreis", "Dämon beschwören"));
        saveSpellIfAbsent(spell("Geisterportal", "Geisterbeschwörer", 8, 4, 12, 6,
                SpellEffectType.BUFF, 0, "Öffnet Portal in Astralraum; Kontrolle über Benutzer", "Astralportal öffnen"));
        saveSpellIfAbsent(spell("Schattenfessel", "Geisterbeschwörer", 8, 2, 12, 0,
                SpellEffectType.DEBUFF, 0, "Verankert Ziel an seinen Schatten; Ziel Bedrängt", "-4 Initiative (Fessel)",
                StatType.INITIATIVE_STEP, ModifierOperation.ADD, -4.0, TriggerContext.ALWAYS, 3));

        log.info("50 Geisterbeschwörer-Zauber migriert.");
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
                "'COMBAT_OPTION','FREE_ACTION','DODGE','STAND_UP','AUFSPRINGEN'," +
                "'THREADWEAVE','SPELL_CAST','TAUNT','DISTRACT'," +
                "'ACROBATIC_DEFENSE','COMBAT_SENSE','IRON_WILL'))"
            ).executeUpdate();
            log.info("action_type CHECK-Constraint aktualisiert.");
        } catch (Exception e) {
            log.warn("Konnte action_type CHECK-Constraint nicht aktualisieren: {}", e.getMessage());
        }
    }
}
