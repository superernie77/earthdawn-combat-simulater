package com.earthdawn.dto;

import com.earthdawn.model.enums.ActionType;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombatActionResult {
    private String actorName;
    private String targetName;
    private ActionType actionType;

    private boolean aggressiveAttack;
    private int attackStep;
    private RollResult attackRoll;
    private RollResult karmaRoll;
    private int defenseValue;
    private boolean hit;

    private int extraSuccesses;
    private int damageStep;
    private RollResult damageRoll;
    /** Karma-Würfel auf den Schadenswurf (nur bei Krallenhand möglich). */
    private RollResult damageKarmaRoll;
    private int armorValue;
    private int netDamage;
    private boolean woundDealt;
    private int newWounds;
    private int totalWounds;
    private int woundThreshold;
    private boolean targetDefeated;

    private List<String> attackBonusNotes;
    /** Notizen zu Schadensboni (z.B. "Schwachstelle erkennen +6 (noch 2 Runden)"). */
    private List<String> damageBonusNotes;
    /** Notizen zur Verteidigung des Ziels (z.B. "Defensive Haltung +3", "Akrobatische Verteidigung +4"). */
    private List<String> defenseNotes;

    /** Treffer, aber Schaden noch nicht angewandt — Ziel kann Ausweichen versuchen. */
    private boolean hitPendingDodge;
    private Long dodgeDefenderId;
    private int pendingDodgeDamage;

    /** Nahkampf-Treffer — Ziel kann Riposte versuchen (kein Schaden bis Entscheidung). */
    private boolean hitPendingRiposte;
    private Long riposteDefenderId;

    /** Lufttanz: Initiative-Vorsprung ≥ 10 → Bonusangriff ausstehend. */
    private boolean lufttanzBonusReady;
    /** Initiative-Differenz (attacker - defender) zur Anzeige. */
    private int lufttanzInitiativeDiff;

    /** Blattschuss war für diesen Angriff aktiviert (kostete 2 Schaden). */
    private boolean blattschussActive;
    /** Pending: Fehlschlag, weitere Karma einsetzbar. */
    private boolean blattschussCanAddKarma;
    /** Bisher per Blattschuss eingesetzte Karmawürfel. */
    private int blattschussKarmaUsed;
    /** Maximalzahl der Karmawürfel = Talentrang. */
    private int blattschussRank;
    /** Liste der bisherigen Blattschuss-Karmawürfe (separat von normalem karmaRoll). */
    private java.util.List<RollResult> blattschussKarmaRolls;

    /** Niedergeschlagen-Probe Ergebnis (wenn Wunde zugefügt). */
    private KnockdownResult knockdownResult;

    private String description;
}
