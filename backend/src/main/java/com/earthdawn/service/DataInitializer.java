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
            migrateKarmaStepToW6();
            migrateFadenwebenTalents();
            migrateGeisterbeschwoererDiscipline();
            migrateKeineDisziplin();
            migrateEd4Disciplines();
            seedSpells();
            migrateIllusionistSpells();
            migrateGeisterbeschwoererSpells();
            cleanupUnimplementedTalents();
            migrateExtraSuccessEffects();
            migrateUtilityTalents();
            migrateArztSkill();
            migrateWeaponSkills();
            migrateSchwimmenSkill();
            migratePhantomkrieger();
            return;
        }
        log.info("Initialisiere Earthdawn Referenzdaten...");
        seedTalents();
        seedSkills();
        seedDisciplines();
        migrateFadenwebenTalents();
        migrateGeisterbeschwoererDiscipline();
        migrateKeineDisziplin();
        migrateEd4Disciplines();
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
        if (talentRepo.findByName("Riposte").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Riposte")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Pariert einen Nahkampfangriff und schlägt zurück. GES + Rang vs. Angriffswurf des Gegners. " +
                            "Freie Aktion, kostet 2 Überanstrengung. " +
                            "Erfolg: Schaden abgewehrt. Übererfolge: Gegenangriff mit Riposte-Ergebnis als Angriffswurf, Schaden −1 Übererfolg.")
                    .testable(true).attackTalent(false).build());
            log.info("Talent 'Riposte' hinzugefügt.");
        }
        if (talentRepo.findByName("Manövrieren").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Manövrieren")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Taktische Positionierung im Nahkampf. GES + Rang vs. KV des Ziels. " +
                            "Einfache Aktion, kostet 1 Überanstrengung. " +
                            "Pro Erfolg: +2 auf eigene KV (Nahkampf) und +2 auf nächsten Angriff gegen dieses Ziel bis Rundenende.")
                    .testable(true).attackTalent(false).build());
            log.info("Talent 'Manövrieren' hinzugefügt.");
        }
        if (talentRepo.findByName("Tigersprung").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Tigersprung")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Verbessert die Initiative magisch. Rang wird direkt zur Initiativestufe addiert. " +
                            "Freie Aktion, kein Würfelwurf, kostet 1 Überanstrengung. Einmal pro Runde.")
                    .testable(false).attackTalent(false).build());
            log.info("Talent 'Tigersprung' hinzugefügt.");
        }
        if (talentRepo.findByName("Zweitwaffe").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Zweitwaffe")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Führt einen zweiten Nahkampfangriff mit der Nebenhand aus. GES + Rang vs. KV des Ziels. " +
                            "Einfache Aktion (zusätzlich zur Hauptaktion), kostet 1 Überanstrengung. " +
                            "Einmal pro Runde. Schaden wie normaler Nahkampfangriff.")
                    .testable(true).attackTalent(false).build());
            log.info("Talent 'Zweitwaffe' hinzugefügt.");
        }
        if (talentRepo.findByName("Nachtreten").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Nachtreten")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Zusätzlicher waffenloser Nahkampfangriff. GES + Rang vs. KV des Ziels. " +
                            "Einfache Aktion (zusätzlich zur Hauptaktion), kostet 1 Überanstrengung. Einmal pro Runde. " +
                            "Nur gegen Ziele mit niedrigerer Initiative. Schaden: waffenlose Stärkestufe.")
                    .testable(true).attackTalent(false).build());
            log.info("Talent 'Nachtreten' hinzugefügt.");
        }
        if (talentRepo.findByName("Blattschuss").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Blattschuss")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Magisches Schützentalent: erlaubt bei einer Projektil-/Wurfwaffen-Probe " +
                            "bis zu Rang zusätzliche Karmawürfel. Wird vor der Probe angekündigt. " +
                            "Bei Fehlschlag dürfen weitere Karma einzeln eingesetzt und das Ergebnis aufaddiert " +
                            "werden, bis Treffer erreicht oder Rang ausgeschöpft. Nach Treffer kein weiteres " +
                            "Karma. Freie Aktion, kostet 2 Überanstrengung, 1× pro Runde.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Blattschuss' hinzugefügt.");
        }
        if (talentRepo.findByName("Lufttanz").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Lufttanz")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Magisches Bewegungstalent. Lufttanzstufe = GES + Rang. Wird bei der " +
                            "Initiativeprobe statt der reinen GES-Stufe verwendet (Modifikatoren wie " +
                            "Rüstungsmalus bleiben). Bei einem Initiative-Vorsprung von ≥10 (3+ Erfolge) " +
                            "gegen das Ziel eines Nahkampfangriffs darf ein zusätzlicher Nahkampfangriff " +
                            "mit derselben Waffe ausgeführt werden. Freie Aktion in der Ansagephase, " +
                            "kostet 2 Überanstrengung, 1× pro Runde.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Lufttanz' hinzugefügt.");
        }
        if (talentRepo.findByName("Kobrastoß").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Kobrastoß")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Steigert die Reaktionsgeschwindigkeit magisch. Kobrastoßstufe = GES + Rang. " +
                            "Wird bei der Initiativeprobe statt der reinen GES-Stufe verwendet (Modifikatoren " +
                            "wie Rüstungsmalus bleiben). Das Ergebnis wird mit der Initiative eines angesagten " +
                            "Gegners verglichen: je Erfolg +2 auf die erste Angriffsprobe gegen genau diesen " +
                            "Gegner in derselben Runde. Würfelt der Gegner höher, gibt es keinen Bonus — auch " +
                            "dann nicht, wenn er seine Handlung später verzögert. Freie Aktion in der " +
                            "Ansagephase, kostet 2 Überanstrengung, 1× pro Runde.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Kobrastoß' hinzugefügt.");
        }
        if (talentRepo.findByName("Löwenherz").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Löwenherz")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Stärkt die mentale Entschlossenheit magisch. Löwenherzstufe = WIL + Rang. " +
                            "Wird anstelle der normalen Willenskraftstufe verwendet, wenn eine Probe abgelegt " +
                            "wird, um die Wirkung von Talenten, Zaubern oder Fähigkeiten abzuschütteln — " +
                            "sofern diese eine Willenskraftprobe zum Widerstand erlauben. Freie Aktion, " +
                            "kostet 1 Überanstrengung, wirkt bis zum Ende der Runde.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Löwenherz' hinzugefügt.");
        }
        if (talentRepo.findByName("Sprint").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Sprint")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Steigert die Bewegungsrate magisch. Stufe = Rang (kein Attribut wird addiert), " +
                            "kein Würfelwurf nötig: die Bewegungsrate steigt in der aktuellen Runde um den Rang. " +
                            "Einfache Aktion — in derselben Runde ist zusätzlich noch eine Standardaktion " +
                            "(Angriff, Zauber) möglich. Kostet 1 Überanstrengung. Auch als weltliche " +
                            "Fertigkeit (Kategorie Bewegung) erlernbar, nach denselben Regeln.")
                    .testable(false)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Sprint' hinzugefügt.");
        }
        if (!skillRepo.existsByName("Sprint")) {
            skillRepo.save(SkillDefinition.builder()
                    .name("Sprint")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Weltliche Fertigkeitsversion von Sprint: +Rang auf die Bewegungsrate " +
                            "für die aktuelle Runde. Einfache Aktion, kostet 1 Überanstrengung.")
                    .category("Bewegung")
                    .build());
            log.info("Fertigkeit 'Sprint' hinzugefügt.");
        }
        if (talentRepo.findByName("Schwachstelle erkennen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Schwachstelle erkennen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Analysiert magisch die Rüstung eines Gegners. Probe: WAH + Rang vs. " +
                            "max(MV, physische Rüstung) des Ziels. Einfache Aktion, kostet 1 Überanstrengung " +
                            "(verbraucht in dieser Implementierung KEINE Hauptaktion). " +
                            "Bei Erfolg: +2 Schaden pro Erfolg auf physische Angriffe gegen dieses Ziel " +
                            "für Rang Runden. Nicht für Zaubersprüche.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Schwachstelle erkennen' hinzugefügt.");
        }
        if (talentRepo.findByName("Krallenhand").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Krallenhand")
                    .attribute(AttributeType.STRENGTH)
                    .description("Verwandelt die Hände magisch in Klauen für den waffenlosen Kampf. " +
                            "Krallenhand-Stufe = STR + Rang + 3 (ersetzt die Stärkestufe beim Schadenswurf). " +
                            "Einfache Aktion, 0 Überanstrengung. " +
                            "Wird beim Hinzufügen automatisch als Waffe (clawWeapon) im Inventar angelegt; " +
                            "bei Rang-Änderung wird der Schadensbonus angepasst. " +
                            "Karma kann zusätzlich auf den Schadenswurf eingesetzt werden. " +
                            "Klauen können nicht durch Entwaffnen entfernt werden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Krallenhand' hinzugefügt.");
        }
        if (talentRepo.findByName("Holzhaut").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Holzhaut")
                    .attribute(AttributeType.TOUGHNESS)
                    .description("Härtet den Körper magisch. Probe: ZÄH + Rang. Ergebnis wird zur Bewusstlosigkeits- und " +
                            "Todesschwelle addiert. Hauptaktion, kostet 1 Erholungsprobe (kein Strain). " +
                            "Wirkt für Rang Stunden. Beim Beenden: aktueller Schaden wird um das Würfelergebnis reduziert. " +
                            "Effekt bleibt aktiv, auch wenn der Adept bewusstlos wird.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Holzhaut' hinzugefügt.");
        }
        if (talentRepo.findByName(TalentNames.ZAUBERMATRITZE).isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name(TalentNames.ZAUBERMATRITZE)
                    .attribute(AttributeType.PERCEPTION)
                    .description("Eine Zaubermatritze hält einen eingewobenen Zauber bereit. " +
                            "Der Rang entspricht immer dem Kreis des Adepten. " +
                            "Kann bis zu 3-mal separat gelernt werden (3 Matrizen). " +
                            "Probe: WN + Rang zum Einweben.")
                    .testable(true)
                    .attackTalent(false)
                    .maxInstances(3)
                    .rankFromCircle(true)
                    .build());
            log.info("Talent 'Zaubermatritze' hinzugefügt.");
        }
        if (talentRepo.findByName(TalentNames.ERWEITERTE_MATRIZE).isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name(TalentNames.ERWEITERTE_MATRIZE)
                    .attribute(AttributeType.PERCEPTION)
                    .description("Eine erweiterte Matrize hält einen eingewobenen Zauber bereit — " +
                            "ein Faden ist bereits gewoben, sobald ein Zauber in der Matrize liegt " +
                            "(benötigter Fadenweben-Aufwand −1). Der Rang entspricht immer dem Kreis des Adepten. " +
                            "Kann bis zu 3-mal separat gelernt werden. Probe: WN + Rang zum Einweben.")
                    .testable(true)
                    .attackTalent(false)
                    .maxInstances(3)
                    .rankFromCircle(true)
                    .build());
            log.info("Talent 'Erweiterte Matrize' hinzugefügt.");
        }
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

        if (talentRepo.findByName("Magie neutralisieren").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Magie neutralisieren")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Beendet einen aktiven magischen Effekt auf einem beliebigen Kombattanten " +
                            "(WIL + Rang vs. Effektstufe + 10). Verbraucht die Aktion der Runde, kostet 1 Überanstrengung. " +
                            "Die Stufe des Effekts wird beim Anwenden gewählt (Effekte tragen keine eigene Stufe — " +
                            "maßgeblich ist der auslösende Zauber bzw. das Talent). Welche Effekte neutralisierbar sind, " +
                            "entscheidet der Spielleiter — es stehen alle zur Auswahl.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Magie neutralisieren' hinzugefügt.");
        }

        if (talentRepo.findByName("Verängstigen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Verängstigen")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Jagt einem Gegner übernatürliche Furcht ein (WIL + Rang vs. Mystische Verteidigung des Ziels). " +
                            "Standardaktion, 0 Überanstrengung. Erfolg: −2 auf alle Aktionsproben je Erfolg für Rang Runden. " +
                            "Das Ziel darf in jeder seiner Runden eine Willenskraftprobe gegen die Verängstigen-Stufe (WIL + Rang) ablegen — " +
                            "Erfolg beendet den Effekt vorzeitig. Disziplintalent des Geisterbeschwörers (1. Kreis), Talentoption für Illusionisten (Kreis 5–8).")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Verängstigen' hinzugefügt.");
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
        // Schadenszauber MIT Schaden+2 pro Übererfolg (Wirkungsstufe +2 laut Tabelle)
        java.util.List<String> damageExtra = java.util.List.of(
            // Illusionist
            "Blitz", "Phantomflamme", "Phantomfeuerball",
            "Echte Geschosse", "Illusionäre Geschosse", "Vorgezeichneter Weg",
            // Elementarist (keine Tabelle vorhanden — Standardannahme für reine Schadenszauber)
            "Flammenpfeil", "Eisnadeln", "Feuerball", "Erdbeben",
            // Geisterbeschwörer laut Tabelle (Wirkungsstufe +2)
            "Astralspeer", "Augenblick des Todes", "Staub zu Staub", "Verdorren"
        );
        // Schadenszauber mit Dauer-Übererfolg (kein Damage-Bonus)
        java.util.List<String> durationExtra = java.util.List.of(
            // Illusionist
            "Illusionärer Blitz", "Ersticken", "Suggestive Stimme",
            "Tanzender Drache", "Gedächtnisnotiz", "Band der Verschwiegenheit",
            "Halt, Stehenbleiben", "Gedankennebel", "Rebellische Gliedmaße",
            "Phantomkrieger",
            // Geisterbeschwörer laut Tabelle (Wirkungsdauer verlängern)
            "Kreis der Kälte", "Astralschlund", "Herzbeklemmung", "Üble Dämpfe"
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
        // Achtung: Nur wirklich NICHT implementierte Talente listen — Löschung entfernt auch
        // Charakter-Zuweisungen! ("Schwimmen" ist seit 1.1.0 implementiert und darf nicht mehr rein.)
        java.util.List<String> toRemove = java.util.List.of(
            "Initiative", "Schlossknacken", "Schleichen",
            "Zähigkeit", "Arkane Waffe", "Fadenmagie", "Erste Hilfe",
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

        // Zusatzfaden-Optionen an bestehende Zauber hängen
        migrateSpellThreadOptions();
    }

    /**
     * Zusatzfaden-Optionen der Zauber (idempotent — nur wenn noch keine gesetzt sind).
     * Nur WIRKUNGSSTUFE wird von der Engine verrechnet; alle übrigen Optionen sind Anzeige
     * für den Spielleiter, weil die Engine weder Reichweiten noch Mehrfachziele noch Boni
     * auf Nicht-Kampf-Proben (Heimlichkeit/Wahrnehmung) kennt.
     */
    private void migrateSpellThreadOptions() {
        String REICHWEITE_10 = "Reichweite Erhöhen (+10 Schritt)";
        String DAUER_2MIN    = "Wirkungsdauer Verlängern (+2 Minuten)";
        String ZIEL_1        = "Zusätzliches Ziel (+1)";
        String ZIEL_RANG     = "Zusätzliches Ziel (+Rang)";

        setThreadOptions("Katastrophe",
                display(REICHWEITE_10));
        setThreadOptions("Umhang",
                display(REICHWEITE_10), display("Wirkung Verstärken (Bonus +2)"),
                display(DAUER_2MIN), display(ZIEL_RANG));
        setThreadOptions("Vertrauen",
                display(REICHWEITE_10), display(DAUER_2MIN));
        setThreadOptions("Blitz",
                effectStep("Wirkung Verstärken (Wirkungsstufe +2)", 2),
                display("Malus Erhöhen (−2)"),
                display("Malus gilt auch für die zusätzliche Durchschauen-Probe"),
                display(ZIEL_1));
        setThreadOptions("Illusionärer Blitz",
                display(REICHWEITE_10), effectStep("Wirkung Verstärken (Wirkungsstufe +2)", 2),
                display(ZIEL_1));
        setThreadOptions("Blindheit",
                durationRounds(DAUER_2MIN, 20));

        // Bestehende Datenbanken auf die neue Blindheit-Mechanik heben
        migrateBlindheit();

        setThreadOptions("Geisterpfeil",
                display(REICHWEITE_10),
                buffValue("Wirkung Verstärken (Mystische Rüstung −2)", 2),
                effectStep("Wirkung Verstärken (Wirkungsstufe +2)", 2),
                display(ZIEL_1));
        setThreadOptions("Nebelschild",
                buffValue("Wirkung Verstärken (Bonus +2)", 2));
        setThreadOptions("Schmerzen",
                display(REICHWEITE_10),
                buffValue("Wirkung Verstärken (+1 Wunde)", 1));
        setThreadOptions("Schädel des Todes",
                buffValue("Wirkung Verstärken (Bonus +2 auf Verängstigen)", 2));

        // Bestehende Datenbanken auf die neue Geisterbeschwörer-Mechanik heben
        migrateGeisterbeschwoererZauber();
        setThreadOptions("Gedankennebel",
                display(REICHWEITE_10), effectStep("Wirkung Verstärken (Wirkungsstufe +2)", 2),
                display(ZIEL_1));
        setThreadOptions("Sehen von Verborgenem",
                display("Wirkung Verstärken (Bonus +1)"), display(DAUER_2MIN), display(ZIEL_RANG));
        setThreadOptions("Niemand Da",
                display("Reichweite Erhöhen (+2 Schritt)"), display(DAUER_2MIN));
        setThreadOptions("Phantomkrieger",
                display(REICHWEITE_10),
                buffValue("Wirkung Verstärken (+1 Bild: +1 KV, Angreifer −1)", 1),
                display(ZIEL_RANG));

        // Bestehende Datenbanken: die alte Anzeige-Option "+1 Bild" auf verrechnet umstellen
        migratePhantomkriegerBildOption();
    }

    private SpellThreadOption display(String label) {
        return SpellThreadOption.builder()
                .label(label).type(SpellThreadOptionType.DISPLAY).value(0).build();
    }

    /**
     * Geisterpfeil, Nebelschild, Schmerzen, Schädel des Todes auf die mechanisierten
     * Regeln heben (Übererfolge, Grundeffekte). Idempotent für Bestands-DBs.
     */
    private void migrateGeisterbeschwoererZauber() {
        spellRepo.findAll().forEach(sp -> {
            boolean changed = false;
            switch (sp.getName()) {
                case "Geisterpfeil" -> {
                    if (sp.getWeavingDifficulty() == 0) { sp.setWeavingDifficulty(5); changed = true; }
                    if (sp.getDuration() != 2) { sp.setDuration(2); changed = true; }
                    if (!"DURATION".equals(sp.getExtraSuccessEffect())) { sp.setExtraSuccessEffect("DURATION"); changed = true; }
                    if (sp.getEffectDescription() == null || !sp.getEffectDescription().contains("MR −2")) {
                        sp.setEffectDescription("WIL+" + sp.getEffectStep() + " Mystisch, MR −2");
                        sp.setDescription("Ein Geschoss aus Geisterenergie; senkt die Mystische Rüstung des Ziels um 2 für 2 Runden (+2 Runden je Übererfolg)");
                        changed = true;
                    }
                }
                case "Nebelschild" -> {
                    if (sp.getModifyStat() != StatType.DODGE_STEP) {
                        sp.setModifyStat(StatType.DODGE_STEP);
                        sp.setDescription("+4 auf Ausweichen-Proben (schützender Nebel)");
                        changed = true;
                    }
                    if (!"DURATION".equals(sp.getExtraSuccessEffect())) { sp.setExtraSuccessEffect("DURATION"); changed = true; }
                }
                case "Schmerzen" -> {
                    if (sp.getModifyStat() != StatType.ATTACK_STEP) {
                        sp.setModifyStat(StatType.ATTACK_STEP);
                        sp.setModifyValue(-3.0);
                        sp.setDescription("3 temporäre Wunden (−3 auf alle Proben) und halbe Bewegungsrate");
                        sp.setEffectDescription("−3 auf alle Proben; halbe Bewegung");
                        changed = true;
                    }
                    if (!"DURATION".equals(sp.getExtraSuccessEffect())) { sp.setExtraSuccessEffect("DURATION"); changed = true; }
                }
                case "Schädel des Todes" -> {
                    if (sp.getEffectDescription() == null || !sp.getEffectDescription().contains("ohne Hauptaktion")) {
                        sp.setDescription("Blutiger Schädel: Verängstigen kostet keine Hauptaktion (Spruchzauberei-Rang + 5 Runden, +2 je Übererfolg)");
                        sp.setEffectDescription("Verängstigen ohne Hauptaktion");
                        changed = true;
                    }
                }
                default -> { }
            }
            if (changed) spellRepo.save(sp);
        });
    }

    private SpellThreadOption durationRounds(String label, int rounds) {
        return SpellThreadOption.builder()
                .label(label).type(SpellThreadOptionType.DURATION_ROUNDS).value(rounds).build();
    }

    /** Wendet eine Nachbearbeitung auf eine Spell-Definition an (für Seed-Sonderfälle). */
    private SpellDefinition spellWith(SpellDefinition s, java.util.function.Consumer<SpellDefinition> f) {
        f.accept(s);
        return s;
    }

    /**
     * Blindheit: −4 auf alle Proben (statt −3 Angriff), Übererfolge +2 Minuten,
     * Zusatzfaden "+2 Minuten" wird verrechnet (20 Runden). Idempotent für Bestands-DBs.
     */
    private void migrateBlindheit() {
        spellRepo.findAll().stream()
                .filter(s -> "Blindheit".equals(s.getName()))
                .forEach(s -> {
                    boolean changed = false;
                    if (s.getModifyValue() != -4.0) { s.setModifyValue(-4.0); changed = true; }
                    if (!"DURATION_MINUTES".equals(s.getExtraSuccessEffect())) {
                        s.setExtraSuccessEffect("DURATION_MINUTES");
                        changed = true;
                    }
                    if (!"−4 auf alle Proben (blind)".equals(s.getEffectDescription())) {
                        s.setEffectDescription("−4 auf alle Proben (blind)");
                        s.setDescription("Ziel sieht nur Schwärze; −4 auf alle Proben. "
                                + "Aktionsprobe über 17 durchschaut die Illusion und beendet sie.");
                        changed = true;
                    }
                    for (SpellThreadOption o : s.getThreadOptions()) {
                        if (o.getLabel() != null && o.getLabel().contains("+2 Minuten")
                                && o.getType() != SpellThreadOptionType.DURATION_ROUNDS) {
                            o.setType(SpellThreadOptionType.DURATION_ROUNDS);
                            o.setValue(20);
                            changed = true;
                        }
                    }
                    if (changed) spellRepo.save(s);
                });
    }

    private SpellThreadOption buffValue(String label, int value) {
        return SpellThreadOption.builder()
                .label(label).type(SpellThreadOptionType.BUFF_VALUE).value(value).build();
    }

    /** Stellt die frühere Anzeige-Option "+1 Bild" des Phantomkriegers auf BUFF_VALUE(1) um. */
    private void migratePhantomkriegerBildOption() {
        spellRepo.findAll().stream()
                .filter(s -> "Phantomkrieger".equals(s.getName()))
                .forEach(s -> {
                    boolean changed = false;
                    for (SpellThreadOption o : s.getThreadOptions()) {
                        if (o.getLabel() != null && o.getLabel().contains("+1 Bild")
                                && o.getType() != SpellThreadOptionType.BUFF_VALUE) {
                            o.setType(SpellThreadOptionType.BUFF_VALUE);
                            o.setValue(1);
                            o.setLabel("Wirkung Verstärken (+1 Bild: +1 KV, Angreifer −1)");
                            changed = true;
                        }
                    }
                    if (changed) spellRepo.save(s);
                });
    }

    private SpellThreadOption effectStep(String label, int value) {
        return SpellThreadOption.builder()
                .label(label).type(SpellThreadOptionType.EFFECT_STEP).value(value).build();
    }

    /** Setzt die Optionen für alle Zauber dieses Namens, sofern dort noch keine hinterlegt sind. */
    private void setThreadOptions(String spellName, SpellThreadOption... options) {
        spellRepo.findAll().stream()
                .filter(s -> spellName.equals(s.getName()))
                .filter(s -> s.getThreadOptions() == null || s.getThreadOptions().isEmpty())
                .forEach(s -> {
                    s.setThreadOptions(new java.util.ArrayList<>(java.util.List.of(options)));
                    spellRepo.save(s);
                });
    }

    private void migrateDisciplineAccessLists() {
        // Hinweis: Disziplin-Namen wie sie nach migrateDisciplineBonuses() in der DB stehen
        java.util.Map<String, java.util.List<String>> accessMap = new java.util.HashMap<>();
        accessMap.put("Krieger",        java.util.List.of("Nahkampfwaffen", "Ausweichen", "Standhaftigkeit", "Verspotten", "Kampfsinn", "Akrobatische Verteidigung"));
        accessMap.put("Kundschafter",   java.util.List.of("Projektilwaffen", "Wurfwaffen", "Ausweichen", "Magische Markierung"));
        accessMap.put("Dieb",           java.util.List.of("Nahkampfwaffen", "Waffenloser Kampf", "Ausweichen", "Akrobatische Verteidigung", "Ablenken"));
        accessMap.put("Elementarist",   java.util.List.of("Spruchzauberei", "Elementarismus", "Eiserner Wille", "Standhaftigkeit"));
        accessMap.put("Magier",         java.util.List.of("Spruchzauberei", "Magie", "Eiserner Wille", "Standhaftigkeit", "Starrsinn"));
        accessMap.put("Illusionist",    java.util.List.of("Spruchzauberei", "Illusionismus", "Eiserner Wille", "Verspotten", "Ablenken", "Verängstigen"));
        accessMap.put("Schwertmeister", java.util.List.of("Nahkampfwaffen", "Ausweichen", "Kampfsinn", "Akrobatische Verteidigung", "Standhaftigkeit"));
        accessMap.put("Troubadour",     java.util.List.of("Verspotten", "Ablenken", "Ausweichen", "Magische Markierung"));
        accessMap.put("Geisterbeschwörer", java.util.List.of("Spruchzauberei", "Geisterbeschwörung", "Eiserner Wille", "Standhaftigkeit", "Starrsinn", "Verängstigen"));
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

        // Generisches Fadenweben (<Disziplin>) in die Zugriffsliste einhängen (idempotent).
        for (String disc : disziplinenOhneWebtalent()) {
            disciplineRepo.findByName(disc).ifPresent(d -> {
                String tname = fadenwebenTalentName(disc);
                if (!d.getAccessTalentNames().contains(tname)) {
                    d.getAccessTalentNames().add(tname);
                    disciplineRepo.save(d);
                }
            });
        }
    }

    private void migrateKarmaModifier() {
        for (GameCharacter c : characterRepo.findAll()) {
            boolean hasNoKarma = c.getDiscipline() != null
                    && "Keine Disziplin".equals(c.getDiscipline().getName());
            if (hasNoKarma) {
                c.setKarmaModifier(0);
                c.setKarmaMax(0);
                c.setKarmaCurrent(0);
            } else {
                if (c.getKarmaModifier() == 0) {
                    c.setKarmaModifier(5);
                }
                c.setKarmaMax(c.getKarmaModifier() * c.getCircle());
            }
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
            skill("Theologie",     AttributeType.PERCEPTION, "Religion und Götter",    "Wissen"),
            skill("Nahkampfwaffen", AttributeType.DEXTERITY, "Nahkampfangriff als Fertigkeit (GES + Rang vs. KV). Wie das Talent, aber ohne Karma.", "Waffen"),
            skill("Projektilwaffen", AttributeType.DEXTERITY, "Fernkampfangriff als Fertigkeit (GES + Rang vs. KV). Wie das Talent, aber ohne Karma.", "Waffen"),
            skill("Schwimmen",     AttributeType.STRENGTH,   "Schwimmt durch Gewässer und gegen Strömungen. STÄ + Rang vs. Schwierigkeitswert.", "Bewegung")
        );
        skillRepo.saveAll(skills);
    }

    /** Idempotent: fügt die Fertigkeit Schwimmen hinzu (für bestehende DBs). STÄ-basiert. */
    private void migrateSchwimmenSkill() {
        if (!skillRepo.existsByName("Schwimmen")) {
            skillRepo.save(SkillDefinition.builder()
                    .name("Schwimmen").attribute(AttributeType.STRENGTH)
                    .description("Schwimmt durch Gewässer und gegen Strömungen. STÄ + Rang vs. Schwierigkeitswert.")
                    .category("Bewegung").build());
            log.info("Fertigkeit 'Schwimmen' hinzugefügt.");
        }
    }

    /** Idempotent: fügt die Waffen-Fertigkeiten hinzu (für bestehende DBs). Funktionieren wie die
     *  gleichnamigen Talente im Kampf, erlauben aber kein Karma. */
    private void migrateWeaponSkills() {
        if (!skillRepo.existsByName("Nahkampfwaffen")) {
            skillRepo.save(SkillDefinition.builder()
                    .name("Nahkampfwaffen").attribute(AttributeType.DEXTERITY)
                    .description("Nahkampfangriff als Fertigkeit (GES + Rang vs. KV). Wie das Talent, aber ohne Karma.")
                    .category("Waffen").build());
            log.info("Fertigkeit 'Nahkampfwaffen' hinzugefügt.");
        }
        if (!skillRepo.existsByName("Projektilwaffen")) {
            skillRepo.save(SkillDefinition.builder()
                    .name("Projektilwaffen").attribute(AttributeType.DEXTERITY)
                    .description("Fernkampfangriff als Fertigkeit (GES + Rang vs. KV). Wie das Talent, aber ohne Karma.")
                    .category("Waffen").build());
            log.info("Fertigkeit 'Projektilwaffen' hinzugefügt.");
        }
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

        // Generisches "Fadenweben (<Disziplin>)" für alle übrigen Disziplinen. Reines
        // Fertigkeits-/Talent-Weben (Fäden zu magischen Gegenständen/Mustern) — KEINE
        // Kampfrelevanz, spielt nicht ins Spruch-Fadenweben hinein. Die vier magischen
        // Disziplinen haben ihr Webtalent bereits (Elementarismus etc.).
        for (String disc : disziplinenOhneWebtalent()) {
            String tname = fadenwebenTalentName(disc);
            if (talentRepo.findByName(tname).isEmpty()) {
                talentRepo.save(TalentDefinition.builder()
                        .name(tname)
                        .attribute(AttributeType.PERCEPTION)
                        .description("Webt Fäden zu Mustern und magischen Gegenständen (WAH + Rang). "
                                + "Disziplintalent des " + disc + ".")
                        .testable(true)
                        .attackTalent(false)
                        .build());
                log.info("Fadenweben-Talent '{}' hinzugefügt.", tname);
            }
        }
    }

    /** Name des generischen Fadenweben-Talents einer Disziplin. */
    private String fadenwebenTalentName(String discipline) {
        return "Fadenweben (" + discipline + ")";
    }

    /**
     * Alle Disziplinen (außer "Keine Disziplin"), die noch KEIN eigenes Web-Talent haben —
     * die vier magischen Disziplinen weben über Elementarismus/Illusionismus/Magie/
     * Geisterbeschwörung und bekommen daher kein generisches Fadenweben.
     */
    private java.util.List<String> disziplinenOhneWebtalent() {
        java.util.Set<String> mitEigenemWebtalent = java.util.Set.of(
                "Keine Disziplin", "Elementarist", "Illusionist", "Magier", "Geisterbeschwörer");
        return disciplineRepo.findAll().stream()
                .map(DisciplineDefinition::getName)
                .filter(n -> !mitEigenemWebtalent.contains(n))
                .toList();
    }

    // --- Geisterbeschwörer-Disziplin ---

    private void migrateGeisterbeschwoererDiscipline() {
        if (disciplineRepo.findByName("Geisterbeschwörer").isEmpty()) {
            disciplineRepo.save(DisciplineDefinition.builder()
                    .name("Geisterbeschwörer")
                    .karmaStep(4)
                    .bwBonusPerCircle(3)
                    .tdBonusPerCircle(4)
                    .description("Meister der Geistermagie und der Verbindung zu den Toten.")
                    .accessTalentNames(new java.util.ArrayList<>(List.of(
                            "Spruchzauberei", "Fadenmagie", "Geisterbeschwörung", "Standhalten", "Meditation")))
                    .build());
            log.info("Disziplin 'Geisterbeschwörer' hinzugefügt.");
        }
    }

    private void migrateKeineDisziplin() {
        if (disciplineRepo.findByName("Keine Disziplin").isEmpty()) {
            disciplineRepo.save(DisciplineDefinition.builder()
                    .name("Keine Disziplin")
                    .karmaStep(0)
                    .bwBonusPerCircle(0)
                    .tdBonusPerCircle(0)
                    .description("Kein Abenteurer einer Disziplin. Kein Karma.")
                    .accessTalentNames(new java.util.ArrayList<>())
                    .build());
            log.info("Disziplin 'Keine Disziplin' hinzugefügt.");
        }
    }

    private void migrateKarmaStepToW6() {
        // ED4 FASA: karma die is always W6 (Step 4) for every discipline except Keine Disziplin
        disciplineRepo.findAll().forEach(d -> {
            if (d.getKarmaStep() != 0 && d.getKarmaStep() != 4) {
                d.setKarmaStep(4);
                disciplineRepo.save(d);
            }
        });
        log.info("Karma-Step aller Disziplinen auf W6 (Step 4) gesetzt.");
    }

    private void migrateEd4Disciplines() {
        record D(String name, int karmaStep, int bw, int td, List<String> talents) {}
        List<D> toAdd = List.of(
            new D("Luftpirat",    4, 7, 8, List.of("Nahkampfwaffen", "Projektilwaffen", "Ausweichen", "Standhaftigkeit")),
            new D("Luftsegler",   4, 5, 6, List.of("Nahkampfwaffen", "Ausweichen", "Verspotten")),
            new D("Schütze",      4, 5, 6, List.of("Projektilwaffen", "Wurfwaffen", "Magische Markierung", "Ausweichen")),
            new D("Steppenreiter",4, 7, 8, List.of("Nahkampfwaffen", "Projektilwaffen", "Standhaftigkeit", "Kampfsinn")),
            new D("Tiermeister",  4, 5, 6, List.of("Ausweichen", "Standhaftigkeit", "Kampfsinn")),
            new D("Waffenschmied",4, 5, 6, List.of("Nahkampfwaffen", "Standhaftigkeit"))
        );
        for (D d : toAdd) {
            if (disciplineRepo.findByName(d.name()).isEmpty()) {
                disciplineRepo.save(DisciplineDefinition.builder()
                        .name(d.name())
                        .karmaStep(d.karmaStep())
                        .bwBonusPerCircle(d.bw())
                        .tdBonusPerCircle(d.td())
                        .accessTalentNames(new java.util.ArrayList<>(d.talents()))
                        .build());
                log.info("Disziplin '{}' hinzugefügt.", d.name());
            }
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
        saveSpellIfAbsent(spellWith(spell("Blindheit", "Illusionist", 2, 1, 6, 0,
                SpellEffectType.DEBUFF, 0,
                "Ziel sieht nur Schwärze; −4 auf alle Proben. Aktionsprobe über 17 durchschaut die Illusion und beendet sie.",
                "−4 auf alle Proben (blind)",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -4.0, TriggerContext.ALWAYS, 3),
                s -> s.setExtraSuccessEffect("DURATION_MINUTES")));
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
        saveSpellIfAbsent(SpellDefinition.builder()
                .name("Phantomkrieger").discipline("Illusionist").circle(3)
                .threads(1).weavingDifficulty(7).castingDifficulty(0)
                .effectType(SpellEffectType.BUFF).effectStep(0)
                .description("Erschafft 3 Abbilder des Ziels: +3 KV auf das Ziel; Angriffe gegen das Ziel erleiden −3. Wirkschwierigkeit = MV des Ziels.")
                .effectDescription("+3 KV; Angreifer −3")
                .modifyStat(StatType.PHYSICAL_DEFENSE).modifyOperation(ModifierOperation.ADD)
                .modifyValue(3.0).modifyTrigger(TriggerContext.ALWAYS).duration(3)
                .requiresTarget(true)
                .build());
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
        saveSpellIfAbsent(spellWith(spell("Geisterpfeil", "Geisterbeschwörer", 1, 0, 5, 0,
                SpellEffectType.DAMAGE, 2,
                "WIL+2/Mystisch; senkt die Mystische Rüstung des Ziels um 2 für 2 Runden (+2 Runden je Übererfolg)",
                "WIL+2 Mystisch, MR −2"),
                sp -> { sp.setDuration(2); sp.setExtraSuccessEffect("DURATION"); }));
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
        saveSpellIfAbsent(spellWith(spell("Nebelschild", "Geisterbeschwörer", 2, 0, 6, 0,
                SpellEffectType.BUFF, 0, "+4 auf Ausweichen-Proben (schützender Nebel)", "+4 Ausweichen",
                StatType.DODGE_STEP, ModifierOperation.ADD, 4.0, TriggerContext.ALWAYS, 3),
                sp -> sp.setExtraSuccessEffect("DURATION")));
        saveSpellIfAbsent(spell("Schädel des Todes", "Geisterbeschwörer", 2, 0, 6, 0,
                SpellEffectType.BUFF, 0,
                "Blutiger Schädel: Verängstigen kostet keine Hauptaktion (Spruchzauberei-Rang + 5 Runden, +2 je Übererfolg)",
                "Verängstigen ohne Hauptaktion"));
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
        saveSpellIfAbsent(spellWith(spell("Schmerzen", "Geisterbeschwörer", 3, 0, 7, 0,
                SpellEffectType.DEBUFF, 0,
                "3 temporäre Wunden (−3 auf alle Proben) und halbe Bewegungsrate",
                "−3 auf alle Proben; halbe Bewegung",
                StatType.ATTACK_STEP, ModifierOperation.ADD, -3.0, TriggerContext.ALWAYS, 3),
                sp -> sp.setExtraSuccessEffect("DURATION")));

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
     * Idempotente Migration: fügt Utility-Talente hinzu, die nicht kampfrelevant sind,
     * aber im Dice Roller zur Auswahl stehen sollen.
     */
    private void migrateUtilityTalents() {
        if (talentRepo.findByName("Aufmerksamkeit").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Aufmerksamkeit")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Bemerkt Details und verborgene Dinge in der Umgebung. WAH + Rang vs. Schwierigkeitswert.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Aufmerksamkeit' hinzugefügt.");
        }
        if (talentRepo.findByName("Heimlicher Schritt").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Heimlicher Schritt")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Bewegt sich lautlos und unbemerkt. GES + Rang vs. Wahrnehmung des Beobachters.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Heimlicher Schritt' hinzugefügt.");
        }
        if (talentRepo.findByName("Mystische Verfolgung").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Mystische Verfolgung")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Verfolgt magische Spuren und Astrallinien. WAH + Rang vs. Schwierigkeitswert der Spur.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Mystische Verfolgung' hinzugefügt.");
        }
        if (talentRepo.findByName("Klettern").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Klettern")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Erklettert Oberflächen und Hindernisse. GES + Rang vs. Schwierigkeitswert der Oberfläche.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Klettern' hinzugefügt.");
        }
        if (talentRepo.findByName("Schwimmen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Schwimmen")
                    .attribute(AttributeType.STRENGTH)
                    .description("Schwimmt durch Gewässer und gegen Strömungen. STÄ + Rang vs. Schwierigkeitswert.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Schwimmen' hinzugefügt.");
        }
        if (talentRepo.findByName("Spurenlesen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Spurenlesen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Findet und verfolgt physische Spuren. WAH + Rang vs. Schwierigkeitswert der Spur.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Spurenlesen' hinzugefügt.");
        }
        if (talentRepo.findByName("Wildnis Überleben").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Wildnis Überleben")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Überlebt in der Wildnis: Nahrung, Unterschlupf, Navigation. WAH + Rang vs. Schwierigkeitswert.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Wildnis Überleben' hinzugefügt.");
        }
        if (talentRepo.findByName("Waffen Schmieden").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Waffen Schmieden")
                    .attribute(AttributeType.STRENGTH)
                    .description("Fertigt und repariert Waffen. STR + Rang vs. Schwierigkeitswert der Waffe.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Waffen Schmieden' hinzugefügt.");
        }
        if (talentRepo.findByName("Tierfreundschaft").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Tierfreundschaft")
                    .attribute(AttributeType.CHARISMA)
                    .description("Beruhigt und beeinflusst Tiere. CHA + Rang vs. Soziale Verteidigung des Tieres.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Tierfreundschaft' hinzugefügt.");
        }
        if (talentRepo.findByName("Herzliches Lachen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Herzliches Lachen")
                    .attribute(AttributeType.CHARISMA)
                    .description("Stärkt mit befreiendem Lachen die Moral der Verbündeten: Einfache Aktion, "
                            + "1 Überanstrengung. CHA + Rang vs. höchste Soziale VK der Gegner. Bei Erfolg +2 "
                            + "pro Erfolg auf Soziale VK und Furcht-Widerstand aller Verbündeten für Rang Runden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Herzliches Lachen' hinzugefügt.");
        }
        if (talentRepo.findByName("Erster Eindruck").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Erster Eindruck")
                    .attribute(AttributeType.CHARISMA)
                    .description("Hinterlässt bei einer ersten Begegnung einen gezielten Eindruck. CHA + Rang vs. Soziale Verteidigung.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Erster Eindruck' hinzugefügt.");
        }
        if (talentRepo.findByName("Gefühlsmelodie").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Gefühlsmelodie")
                    .attribute(AttributeType.CHARISMA)
                    .description("Weckt mit Musik oder Gesang bestimmte Gefühle im Publikum. CHA + Rang vs. Soziale Verteidigung.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Gefühlsmelodie' hinzugefügt.");
        }
        if (talentRepo.findByName("Etikette").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Etikette")
                    .attribute(AttributeType.CHARISMA)
                    .description("Kennt Sitten, Gebräuche und angemessenes Verhalten verschiedener Gesellschaften. CHA + Rang vs. Schwierigkeitswert.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Etikette' hinzugefügt.");
        }
        if (talentRepo.findByName("Empathische Wahrnehmung").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Empathische Wahrnehmung")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Erfasst die Gefühle und Stimmungen anderer. WAH + Rang vs. Soziale Verteidigung.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Empathische Wahrnehmung' hinzugefügt.");
        }
        if (talentRepo.findByName("Forschen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Forschen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Findet Informationen in Bibliotheken, Archiven und durch Nachforschungen. WAH + Rang vs. Schwierigkeitswert.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Forschen' hinzugefügt.");
        }
        if (talentRepo.findByName("Fremdsprachen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Fremdsprachen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Versteht und spricht fremde Sprachen. WAH + Rang vs. Schwierigkeitswert der Sprache.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Fremdsprachen' hinzugefügt.");
        }
        if (talentRepo.findByName("Einschätzen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Einschätzen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Schätzt Kampfkraft, Rang oder Absichten eines Gegenübers ab. WAH + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Einschätzen' hinzugefügt.");
        }
        if (talentRepo.findByName("Beeindrucken").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Beeindrucken")
                    .attribute(AttributeType.STRENGTH)
                    .description("Beeindruckt durch eine Zurschaustellung von Kraft und Können. STÄ + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Beeindrucken' hinzugefügt.");
        }
        if (talentRepo.findByName("Gefahrensinn").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Gefahrensinn")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Spürt drohende Gefahr, bevor sie sichtbar wird. WAH + Rang vs. Schwierigkeitswert der Bedrohung.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Gefahrensinn' hinzugefügt.");
        }
        if (talentRepo.findByName("Gewinnendes Lächeln").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Gewinnendes Lächeln")
                    .attribute(AttributeType.CHARISMA)
                    .description("Entwaffnet ein Gegenüber mit charmantem Auftreten. CHA + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Gewinnendes Lächeln' hinzugefügt.");
        }
        if (talentRepo.findByName("Bleibender Eindruck").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Bleibender Eindruck")
                    .attribute(AttributeType.CHARISMA)
                    .description("Bleibt einem Gegenüber dauerhaft im Gedächtnis. CHA + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Bleibender Eindruck' hinzugefügt.");
        }
        if (talentRepo.findByName("Eleganter Abgang").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Eleganter Abgang")
                    .attribute(AttributeType.CHARISMA)
                    .description("Verlässt eine Szene mit Stil und ohne Gesichtsverlust. CHA + Rang vs. Soziale Verteidigung der Zuschauer.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Eleganter Abgang' hinzugefügt.");
        }
        if (talentRepo.findByName("Wortgeplänkel").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Wortgeplänkel")
                    .attribute(AttributeType.CHARISMA)
                    .description("Führt einen Schlagabtausch aus Spott und Witz. CHA + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Wortgeplänkel' hinzugefügt.");
        }
        if (talentRepo.findByName("Luftgleiten").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Luftgleiten")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Gleitet kontrolliert durch die Luft und mildert Stürze. GES + Rang vs. Schwierigkeitswert des Manövers.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Luftgleiten' hinzugefügt.");
        }
        if (talentRepo.findByName("Struktur Verstehen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Struktur Verstehen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Versteht magische Schriften und Muster und lernt daraus neue Zauber. WAH + Rang vs. Schwierigkeitswert des Musters.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Struktur Verstehen' hinzugefügt.");
        }
        if (talentRepo.findByName("Konversation").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Konversation")
                    .attribute(AttributeType.CHARISMA)
                    .description("Hinterlässt im Gespräch einen vorteilhaften Eindruck und lenkt soziale Situationen. CHA + Rang vs. Soziale Verteidigung des Gegenübers.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Konversation' hinzugefügt.");
        }
        if (talentRepo.findByName("Magische Maske").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Magische Maske")
                    .attribute(AttributeType.CHARISMA)
                    .description("Verkleidet den Adepten magisch als einen anderen Namensgeber. CHA + Rang vs. Wahrnehmung des Beobachters.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Magische Maske' hinzugefügt.");
        }
        if (talentRepo.findByName("Arkanes Gefasel").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Arkanes Gefasel")
                    .attribute(AttributeType.CHARISMA)
                    .description("Beeindruckt oder verwirrt Zuhörer mit magisch klingendem Kauderwelsch. CHA + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Arkanes Gefasel' hinzugefügt.");
        }
        if (talentRepo.findByName("Astralsicht").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Astralsicht")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Nimmt den Astralraum und die Muster magischer Dinge wahr. WAH + Rang vs. Schwierigkeitswert des Musters.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Astralsicht' hinzugefügt.");
        }
        if (talentRepo.findByName("Stimmen Imitieren").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Stimmen Imitieren")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Ahmt Stimmen und Geräusche täuschend echt nach. WAH + Rang vs. Wahrnehmung des Zuhörers.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Stimmen Imitieren' hinzugefügt.");
        }
        if (talentRepo.findByName("Totstellen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Totstellen")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Täuscht den eigenen Tod vor: Atmung und Herzschlag werden nahezu unmerklich. WIL + Rang vs. Wahrnehmung des Beobachters.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Totstellen' hinzugefügt.");
        }
        if (talentRepo.findByName("Flinke Hand").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Flinke Hand")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Magische Taschenspielertricks: Gegenstände verschwinden und tauchen wieder auf. GES + Rang vs. Wahrnehmung des Beobachters.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Flinke Hand' hinzugefügt.");
        }
        if (talentRepo.findByName("Gegenstand Verbergen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Gegenstand Verbergen")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Versteckt Gegenstände magisch am eigenen Körper. GES + Rang vs. Wahrnehmung des Durchsuchenden.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Gegenstand Verbergen' hinzugefügt.");
        }
        if (talentRepo.findByName("Schuld Abwälzen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Schuld Abwälzen")
                    .attribute(AttributeType.CHARISMA)
                    .description("Lenkt Verdacht und Schuld glaubhaft auf jemand anderen. CHA + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Schuld Abwälzen' hinzugefügt.");
        }
        if (talentRepo.findByName("Schloss Knacken").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Schloss Knacken")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Öffnet mechanische Schlösser mit Dietrichen und Fingerspitzengefühl. GES + Rang vs. Schwierigkeitswert des Schlosses.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Schloss Knacken' hinzugefügt.");
        }
        if (talentRepo.findByName("Taschendiebstahl").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Taschendiebstahl")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Entwendet Gegenstände unbemerkt aus Taschen und Gürteln. GES + Rang vs. Wahrnehmung des Opfers.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Taschendiebstahl' hinzugefügt.");
        }
        if (talentRepo.findByName("Fallen Entschärfen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Fallen Entschärfen")
                    .attribute(AttributeType.DEXTERITY)
                    .description("Macht mechanische und magische Fallen unschädlich. GES + Rang vs. Schwierigkeitswert der Falle.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Fallen Entschärfen' hinzugefügt.");
        }
        if (talentRepo.findByName("Feilschen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Feilschen")
                    .attribute(AttributeType.CHARISMA)
                    .description("Handelt Preise und Bedingungen zum eigenen Vorteil aus. CHA + Rang vs. Soziale Verteidigung des Händlers.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Feilschen' hinzugefügt.");
        }
        if (talentRepo.findByName("Weitsprung").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Weitsprung")
                    .attribute(AttributeType.STRENGTH)
                    .description("Erhöht die Sprungdistanz magisch. STÄ + Rang vs. Schwierigkeitswert der Distanz.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Weitsprung' hinzugefügt.");
        }
        if (talentRepo.findByName("Projektil Rufen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Projektil Rufen")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Ruft abgefeuerte Munition magisch in die Hand zurück. WAH + Rang vs. Schwierigkeitswert nach Entfernung.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Projektil Rufen' hinzugefügt.");
        }
        if (talentRepo.findByName("Navigation").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Navigation")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Bestimmt Kurs und Position anhand von Gestirnen, Karten und Landmarken. WAH + Rang vs. Schwierigkeitswert der Route.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Navigation' hinzugefügt.");
        }
        if (talentRepo.findByName("Tieranalyse").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Tieranalyse")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Erkennt Werte, Fähigkeiten und Schwächen einer Kreatur. WAH + Rang vs. Schwierigkeitswert der Kreatur.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Tieranalyse' hinzugefügt.");
        }
        if (talentRepo.findByName("Beweisanalyse").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Beweisanalyse")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Untersucht physische Spuren und Beweise mit Logik und Magie. WAH + Rang vs. Schwierigkeitswert der Beweislage.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Beweisanalyse' hinzugefügt.");
        }
        if (talentRepo.findByName("Geistersprache").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Geistersprache")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Versteht und spricht die Sprachen von Geistern und Astralwesen. WAH + Rang vs. Schwierigkeitswert des Geistes.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Geistersprache' hinzugefügt.");
        }
        if (talentRepo.findByName("Lesen/Schreiben").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Lesen/Schreiben")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Liest und verfasst geschriebene Texte in bekannten Schriften. WAH + Rang vs. Schwierigkeitswert des Textes.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Lesen/Schreiben' hinzugefügt.");
        }
        if (talentRepo.findByName("Lebensblick").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Lebensblick")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Nimmt Lebenskraft und Gesundheitszustand eines Wesens wahr. WAH + Rang vs. Mystische Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Lebensblick' hinzugefügt.");
        }
        if (talentRepo.findByName("Stählerner Blick").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Stählerner Blick")
                    .attribute(AttributeType.CHARISMA)
                    .description("Schüchtert einen Gegner allein durch Augenkontakt ein. CHA + Rang vs. Soziale Verteidigung des Ziels.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Stählerner Blick' hinzugefügt.");
        }
        if (talentRepo.findByName("Nachtflieger Befehligen").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Nachtflieger Befehligen")
                    .attribute(AttributeType.CHARISMA)
                    .description("Befehligt fliegende Kreaturen der Nacht. CHA + Rang vs. Mystische Verteidigung der Kreatur.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Nachtflieger Befehligen' hinzugefügt.");
        }
        if (talentRepo.findByName("Geisterreittier").isEmpty()) {
            talentRepo.save(TalentDefinition.builder()
                    .name("Geisterreittier")
                    .attribute(AttributeType.WILLPOWER)
                    .description("Beschwört ein magisches Reittier aus Nebel und Feuer. WIL + Rang vs. Schwierigkeitswert der Beschwörung.")
                    .testable(true)
                    .attackTalent(false)
                    .build());
            log.info("Talent 'Geisterreittier' hinzugefügt.");
        }
        log.info("Utility-Talente migriert.");
    }

    private void migrateArztSkill() {
        if (!skillRepo.existsByName("Arzt")) {
            skillRepo.save(SkillDefinition.builder()
                    .name("Arzt")
                    .attribute(AttributeType.PERCEPTION)
                    .description("Behandelt Wunden und verbessert Erholungsproben. WN + Rang vs. 6 × Wunden des Patienten. " +
                            "Erfolg: +Rang Bonus-Stufen auf die nächste Erholungsprobe des Patienten.")
                    .category("Handwerk")
                    .build());
            log.info("Fertigkeit 'Arzt' hinzugefügt.");
        }
    }

    /**
     * Setzt requiresTarget=true auf Phantomkrieger (für Instanzen die vor V23 geseedet wurden).
     */
    private void migratePhantomkrieger() {
        spellRepo.findAll().stream()
                .filter(s -> "Phantomkrieger".equals(s.getName()) && !s.isRequiresTarget())
                .forEach(s -> {
                    s.setRequiresTarget(true);
                    s.setDescription("Erschafft 3 Abbilder des Ziels: +3 KV auf das Ziel; Angriffe gegen das Ziel erleiden −3. Wirkschwierigkeit = MV des Ziels.");
                    s.setEffectDescription("+3 KV; Angreifer −3");
                    spellRepo.save(s);
                    log.info("Phantomkrieger: requiresTarget auf true gesetzt.");
                });
    }

}
