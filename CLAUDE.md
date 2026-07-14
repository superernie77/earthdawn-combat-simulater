# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow-Konventionen
- **Changelog immer pflegen**: Bei jedem neuen Feature / Fix den `README.md`-Changelog unter der aktuellen (unveröffentlichten) Version aktualisieren — sowie diese `CLAUDE.md`, wenn sich Architektur/Mechanik/Endpunkte ändern. Gilt für jede Änderung, nicht erst beim Release.

## What This Is
A local dev tool for simulating Earthdawn 4th Edition (FASA) pen-and-paper RPG combat.
Characters with attributes, disciplines, talents and skills are managed and used in
turn-based combat sessions with live updates via WebSocket.

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.5, Maven (`./mvnw`), Spring Data JPA, PostgreSQL, Spring WebSocket (STOMP)
- **Frontend**: Angular 20 standalone components, Angular Material dark theme
- **Database**: PostgreSQL via Docker (`docker compose up -d`)
- **Migrations**: Flyway (schema managed in `backend/src/main/resources/db/migration/`)

## Starting the App
```bash
# PostgreSQL
docker compose up -d

# Backend (from backend/)
./mvnw spring-boot:run

# Frontend (from frontend/)
npm install
npx ng serve
```
Backend: `http://localhost:8081` | Frontend: `http://localhost:4200`

## Running Tests
```bash
# All tests (from backend/)
./mvnw test

# Single test class
./mvnw test -Dtest=StepRollServiceTest

# Single test method
./mvnw test -Dtest=StepRollServiceTest#testStep4
```
Test classes (plain Mockito, no Spring context — fast):
- `StepRollServiceTest` — dice step table, attribute→step mapping, explosion flag
- `ModifierAggregatorTest` — ADD/MULTIPLY/OVERRIDE/SET_MIN/SET_MAX, trigger context filtering
- `CombatServiceDamageTest` — `applyDamageToDefender`: damage, wounds, defeat, knockdown
- `CharacterServiceRecoveryTest` — ZÄH→max-table, slot consumption, wound penalty, Erholungstrank/Heiltrank, reset

## Production Deployment
```bash
# On server: rebuild and redeploy
git pull && docker compose -f docker-compose.prod.yml up -d --build

# Tail backend logs
docker compose -f docker-compose.prod.yml logs -f --tail 100 earthdawn-backend
```

## Earthdawn 4 (FASA) Rules Implemented

### Core Mechanics
- **Step system**: Each step maps to specific dice (Step 4=W6, Step 5=W8, Step 8=2W6, Step 12=2W10, etc.)
- **Exploding dice**: Roll again and add on max value
- **Attributes = Steps**: In ED4 FASA, attribute value directly equals step number (1:1)
- **Probe**: Attribut-Step + Talent-Rang vs Schwierigkeitswert. Extra Erfolge: (Ergebnis - SW) / 5
- **Karma**: Spend 1 Karma → add W6 (Step 4, fixed for all disciplines). ProbeService, CombatService, SpellService all use `diceService.roll(4)`.
- **Erfolge (Successes)**: A basic success = 1 Erfolg. Each 5 over the TN = +1 more Erfolg. So successes = 1 + floor((total − TN) / 5).

### Combat Sequence (per Round)
1. **Ansagephase (DECLARATION)**: All combatants choose stance (NONE/AGGRESSIVE/DEFENSIVE) + action type (WEAPON/SPELL). Stances apply immediately as ActiveEffects with ATTACK_STEP modifier. Initiative rolls automatically once all have declared.
2. **Aktionsphase (ACTION)**: Combatants act in initiative order. Attacks, spells, social/defensive talents, free actions.
3. **Rundenende**: `nextRound()` clears "Aggressiver Angriff" / "Defensive Haltung" effects, ticks down all other ActiveEffect durations, resets declarations → back to DECLARATION.

**Aggressive stance** (+3 ATTACK_STEP, -3 all defenses, 1 damage to self) and **Defensive stance** (-3 ATTACK_STEP, +3 all defenses) are declared in the DECLARATION phase only — not activatable during an attack.

### Spell System
- **Fadenweben** (Thread Weaving): PER-Step + Fadenweben-Talent vs. Schwierigkeitswert. Must weave `spell.threads` threads (one action each) before casting.
- **Spruchzauberei** (Spell Casting): PER-Step + Spruchzauberei-Talent vs. target's spell defense (or fixed difficulty).
- **Effect types**: DAMAGE (rolls WIL-Step + effectStep - armor), BUFF/DEBUFF (adds ActiveEffect), HEAL (rolls effectStep).
- Discipline-to-weaving-talent map: Elementarist→Elementarismus, Illusionist→Illusionismus, Magier→Magie, Geisterbeschwörer→Geisterbeschwörung.
- **Nur Matrix-Zauber im Kampf**: Die Zauberauswahl im Kampf (Faden-weben- und Wirken-Dialog) bietet ausschließlich Zauber an, die in einer **Matrize** (Zaubermatritze **oder** Erweiterte Matrize) einliegen. Frontend `CombatTrackerComponent.matrixSpellIds()` filtert `spellsOf()` und `readySpellsOf()` auf `talent.assignedSpell`-IDs dieser Matrix-Talente.

#### Zusatzfäden (Stufe 1)
Sind **alle Pflichtfäden gewoben**, kauft jeder weitere Faden genau **eine Option** des Zaubers (`SpellDefinition.threadOptions`, `@ElementCollection` mit `@OrderColumn` — die Reihenfolge ist bindend, da Auswahlen als **Index** gespeichert werden).

- **Nur `EFFECT_STEP` wird verrechnet** (+`value` auf `effectStep`, wirkt in `applySpellDamage`/`applySpellHeal`). Alle anderen Optionen sind `DISPLAY`: sie werden gespeichert und im Log/Modal angezeigt, aber **nicht** gerechnet — die Engine kennt weder Reichweiten (kein Distanzsystem) noch Mehrfachziele (`SpellCastRequest.targetCombatantId` ist einzelzielig) noch Boni auf Nicht-Kampf-Proben (Heimlichkeit/Wahrnehmung sind keine `StatType`s). **Wirkungsdauer** ist bewusst `DISPLAY`: `SpellDefinition.duration` zählt **Runden**, die Regel nennt **Minuten**.
- **Obergrenze = Fadenweben-Rang.** Dieselbe Option darf mehrfach gewählt werden (Werte addieren sich).
- **Auch Sofortzauber (`threads == 0`) können Zusatzfäden haben** (z.B. Blitz, Katastrophe) — der `weaveThread`-Guard wirft nur noch, wenn `threads == 0` **und** `threadOptions` leer ist. Der Mindestwurf ist `weavingDifficulty` (auch bei 0-Faden-Zaubern gesetzt).
- **Freier Zusatzfaden aus der Erweiterten Matrize**: Liegt ein **Sofortzauber (`threads == 0`)** in einer Erweiterten Matrize, hat deren vorgewobener Faden keinen Pflichtfaden zu decken und wird in `castSpell` automatisch zum Zusatzfaden — **immer die `EFFECT_STEP`-Option (+2)**, ohne Wurf, ohne Aktion und **ohne** Anrechnung auf den Fadenweben-Rang (kommt oben drauf). Zauber **ohne** `EFFECT_STEP`-Option (z.B. Katastrophe, ein BUFF ohne Wirkungsstufe) erhalten nichts. Zauber **mit** Pflichtfäden bekommen ihn ebenfalls nicht — dort ist der Matrizenfaden über `threadsRequired − 1` bereits verbraucht.
- **Zusatzfäden erhöhen `threadsWoven` nicht** — sie zählen separat über `CombatantState.extraThreadChoices` (CSV der Indizes, z.B. `"0,0,3"`). Dadurch bleibt die Anzeige `2/2 +1` statt `3/2`.
- `ThreadweaveRequest.extraThreadOptionIndex` ist **nur** bei einem Zusatzfaden erforderlich (und wird bei Pflichtfäden ignoriert). `castSpell` löst die Auswahl nur auf, wenn `preparingSpellId` zum Zauber passt; `resetPreparation()` räumt bei Wirken/Abbrechen/Zauberwechsel auf.
- Frontend spiegelt die Regel in `threadweaveIsExtra()` (inkl. Erweiterte-Matrize-Rabatt) und `weavingRankOf()`. Flyway `V36`.

### Free Actions (Freie Aktionen)
- Do **not** consume `hasActedThisRound` — unlimited per round.
- Roll: AttributStep + TalentRank + bonusSteps vs target's defense stat.
- Effect = `extraSuccesses × valuePerSuccess` applied as ActiveEffect.
- Optional damage cost to the user (e.g. Magische Markierung costs 1 damage).
- **Eiserner Wille** is also a free action (does not consume hasActedThisRound).

### Main-Action Combat Talents
These consume `hasActedThisRound = true`. All cost 1 Überanstrengung (damage).

| Talent | Attribute | Roll vs. | Effect |
|---|---|---|---|
| **Verspotten** | CHA | Soziale VK (SV) | −1/Erfolg auf alle Proben+SV des Ziels für Rang Runden. Auto-Starrsinn-Gegenprobe. |
| **Magie neutralisieren** | WIL | Effektstufe + 10 | Beendet einen **beliebigen aktiven Effekt** auf einem beliebigen Kombattanten. **Verbraucht die Aktion** (`hasActedThisRound=true`) + **1 Überanstrengung**. Wurf: WIL-Step + Rang + Bonus − Wunden vs. **gewählter Effektstufe + 10** (Effekte haben keine eigene Stufe → Stufe wird im Dialog eingegeben). Erfolg entfernt den `ActiveEffect`. **Zwei-Stufen-Flow, beide via WebSocket an alle Clients**: `POST .../combatants/{cId}/neutralize-magic/open` broadcastet das Modal `NEUTRALIZE_MAGIC_SELECT` (Payload `actorCombatantId`/`actorName`/`rank`; **kein** State-Change — die Effektliste bauen die Clients via `allActiveEffects()` aus ihrer Session-Kopie); `POST /sessions/{id}/neutralize-magic` (`NeutralizeMagicRequest`) führt aus und broadcastet `NEUTRALIZE_MAGIC` (`NeutralizeMagicResult`). Es wird **nicht gefiltert**, welche Effekte neutralisierbar sind — das entscheidet der GM. Keine Migration nötig. |
| **Verängstigen** | WIL | Mystische VK (MV) | Standardaktion, **0 Überanstrengung**. −2 × Erfolge (1 + Übererfolge) auf `ATTACK_STEP` (Aktionsproben) für Rang Runden; erneutes Wirken ersetzt den Effekt. Effekt trägt `resistTargetNumber` = WIL-Step + Rang des Adepten: das Ziel darf **1×/Runde** (`fearResistUsedThisRound`, Reset in `nextRound()`) via `POST .../combatants/{cId}/resist-fear` eine WIL-Probe (− Wunden) dagegen ablegen — Erfolg entfernt den Effekt. Endpoints `POST /sessions/{id}/fear` (`FearRequest`→`FearResult`, Modal `FEAR`) + resist (`FearResistResult`, Modal `FEAR_RESIST`). Flyway **V35** (`active_effects.resist_target_number`, `combatant_states.fear_resist_used_this_round`). Disziplintalent Geisterbeschwörer (1. Kreis), Talentoption Illusionist (Kreis 5–8) — in beiden Access-Listen. |
| **Ablenken** | CHA | Soziale VK (SV) | −successes KV auf Anwender UND Ziel (Toter Winkel für Verbündete). |
| **Akrobatische Verteidigung** | DEX | Höchste KV aller Gegner | +2 KV/Erfolg für 1 Runde. Erlischt bei Niedergeschlagen. Nicht mit Kampfsinn. |
| **Kampfsinn** | PER | MV des Ziels | +2 KV + +2 Angriff/Erfolg für 1 Runde. Nur vs. Ziele mit niedrigerer Initiative. Nicht mit Akrobatischer Verteidigung. |
| **Manövrieren** | DEX | KV des Ziels | +successes×2 KV (ON_MELEE_DEFENSE) + pending attack bonus für 1 Runde. |
| **Zweitwaffe** | DEX | KV des Ziels | Zusätzlicher Waffenangriff. Eigener once-per-round-Flag (`zweitWaffeUsedThisRound`). Kann nach Hauptangriff oder statt ihm eingesetzt werden. |
| **Nachtreten** | DEX | KV des Ziels | Zusätzlicher **waffenloser** Angriff (Einfache Aktion). Schaden = reine STR-Stufe (+2/Übererfolg), kein Waffenbonus. Eigener once-per-round-Flag (`nachtretenUsedThisRound`), verbraucht **kein** `hasActedThisRound`. **Nur gegen Ziele mit niedrigerer Initiative** (`attacker.initiative > defender.initiative`). Riposte/Ausweichen des Ziels wie beim Nahkampf. Endpoint `POST /sessions/{id}/nachtreten` (`NachtretenRequest`). Flyway V25. |
| **Schwanzangriff** (T'skrang-Rassenfähigkeit) | DEX (Waffenloser Kampf) | KV des Ziels | Zusätzlicher waffenloser Schwanzangriff, **nur für Race `TSKRANG`** (kein Talent nötig). Probe = DEX-Stufe + Waffenloser-Kampf-Rang (Talent **oder** Fertigkeit, 0 wenn keiner) + Bonus − Wunden − 2. Schaden = STR-Stufe (+2/Übererfolg) + optionale **Schwanzwaffe** (`Equipment.tailWeapon=true`, sonst Fehler). **Keine Überanstrengung**, aber **−2 auf alle Proben dieser Runde** als ActiveEffect `Schwanzangriff` (`ATTACK_STEP −2`, 1 Runde). Once-per-round-Flag `schwanzangriffUsedThisRound` (reset in `nextRound()`), verbraucht **kein** `hasActedThisRound`. Riposte/Ausweichen wie beim Nahkampf. Endpoint `POST /sessions/{id}/schwanzangriff` (`SchwanzangriffRequest`). Flyway V33. Mischung aus Krallenhand (STR-Schaden) und Zweitwaffe (Zusatzangriff). |

**Successes formula** for all main-action talents: `1 + floor((total − TN) / 5)` on success.

### Charakterdatenblatt-Talente (außerhalb des Kampfsystems)
- **Holzhaut**: Auf dem Charakterdatenblatt (Attribute-Tab) verfügbar, wenn der Charakter das Talent besitzt. Wurf: `ZÄH-Step + Talentrang` (`StepRollService.attributeToStep` + Rang). Das Wurfergebnis wird als `holzhautBonus` auf `GameCharacter` gespeichert und in `getDerivedStats()` auf Bewusstlosigkeits- und Todesschwelle addiert. Pro Charakter ist nur **ein** Holzhaut-Bonus gleichzeitig aktiv — erneutes Wirken überschreibt den alten Wert. Beim Beenden (`/holzhaut/end`) wird `currentDamage` um den aktiven Bonus reduziert (Puffer-Heilung) und `holzhautBonus` auf 0 zurückgesetzt. Endpoints: `POST /api/characters/{id}/holzhaut` und `POST /api/characters/{id}/holzhaut/end`. Gibt jeweils `HolzhautResult` zurück.

### Blattschuss
- **Mechanik**: Bei Projektil-/Wurfwaffen-Probe (`RANGED_ATTACK`) zusätzliche Karmawürfel — bis zu Talentrang. Nach Fehlschlag dürfen weitere Karma nachgeschossen werden, bis Treffer erreicht oder Rang ausgeschöpft.
- **Ankündigung**: `AttackActionRequest.useBlattschuss=true` setzt 2 Schaden, `blattschussUsedThisRound=true` und (nur bei Fehlschlag) den pending-State.
- **Pending-State auf `CombatantState`**: `pendingBlattschussDefenderId/Total/KarmaUsed/Rank/WeaponId/Defense`. In `nextRound()` zurückgesetzt via `clearBlattschussPending`.
- **Add-Karma-Endpoint**: `POST /api/combat/sessions/{id}/combatants/{cId}/blattschuss-add-karma` rollt W6, addiert auf Total. Bei Treffer (Total ≥ Defense): Schadenswurf mit Übererfolgen, Ausweichen-Trigger, pending löschen. Bei Fehlschlag mit Karma übrig: pending bleibt. Karma erschöpft oder kein Karma mehr: finaler Fehlschlag.
- **Validierung**: nur RANGED_ATTACK; Riposte nicht erlaubt (Fernkampf), Ausweichen schon. Initialer `performAttack`-Aufruf wirft, wenn nicht-RANGED oder Talent fehlt.
- **`CombatActionResult`**: `blattschussActive`, `blattschussCanAddKarma`, `blattschussKarmaUsed`, `blattschussRank` für UI.
- **Flyway V16**: 7 neue Spalten auf `combatant_states`.

### Lufttanz
- **Aktivierung**: Freie Aktion in der **DECLARATION-Phase**, 2 Schaden, 1×/Runde (`lufttanzActivatedThisRound`).
- **Initiative-Effekt**: ActiveEffect mit `+rank` auf `INITIATIVE_STEP` (mathematisch identisch zu „Lufttanzstufe = Rang+DEX statt DEX-Stufe"). Erlischt nach 1 Runde.
- **Bonus-Trigger**: In `performAttack` bei jedem `MELEE_ATTACK` — **unabhängig von Treffer/Fehlschlag** (der Zusatzangriff wird allein durch den Initiative-Vorsprung gewährt, nicht durch einen erfolgreichen Angriff): wenn `attacker.initiative - defender.initiative ≥ 10` und Lufttanz aktiviert und Bonus noch nicht verbraucht → `pendingLufttanzTargetId` + `pendingLufttanzWeaponId` werden auf `attacker` gesetzt. `CombatActionResult.lufttanzBonusReady = true` und `lufttanzInitiativeDiff` zur Anzeige. Der Trigger steht **vor** dem `if (hit)`-Block, damit er auch bei Fehlschlag (und im Riposte/Ausweichen-Early-Return) feuert.
- **Bonus-Angriff**: `POST /api/combat/sessions/{id}/lufttanz-attack` (`LufttanzAttackRequest`). Führt regulären `performAttack` mit gespeichertem Ziel und Waffe aus, verbraucht **kein** `hasActedThisRound`, setzt `lufttanzBonusUsedThisRound=true` und löscht pending-Felder vor dem Aufruf — verhindert dadurch Selbst-Retrigger.
- **CombatantState** Felder: `lufttanzActivatedThisRound`, `lufttanzBonusUsedThisRound`, `pendingLufttanzTargetId` (-1 = none), `pendingLufttanzWeaponId` (-1). Alle 4 werden in `nextRound()` zurückgesetzt.
- **Flyway V15**: 4 neue Spalten auf `combatant_states`.

### Karma auf Initiative (Disziplin-Fähigkeit ab Kreis 3)
- **Berechtigung**: Disziplin ∈ {**Dieb, Kundschafter, Luftsegler, Schütze**} **und** `circle >= 3` (`CombatService.canUseKarmaOnInitiative`).
- **Auswahl**: In der **Ansagephase** (DECLARATION) setzt `POST /api/combat/sessions/{id}/combatants/{cId}/karma-initiative?spend=true|false` das Flag `karmaInitiativeThisRound` auf `CombatantState`. Validiert Phase, Berechtigung und Karma > 0. Karma wird **noch nicht** abgezogen.
- **Einlösung**: In `rerollInitiative` wird — wenn das Flag gesetzt, die Disziplin/Kreis berechtigt und Karma > 0 — **1 Karma abgezogen** und ein **W6 (Stufe 4)** (`diceService.roll(4)`) zur Initiative addiert. Anzeige im Initiative-Modal als `Karma +X` in `bonusNotes`; `total` zeigt Basiswurf + Karma.
- **Reset**: `karmaInitiativeThisRound` wird in `nextRound()` zurückgesetzt.
- **Frontend**: Toggle-Button „Karma-Init" auf der Kombattanten-Karte in der Ansagephase, sichtbar via `canUseKarmaInitiative(c)` (gleiche Disziplin-/Kreis-Bedingung). Deaktiviert ohne Karma.
- **Flyway V31**: `combatant_states.karma_initiative_this_round` (boolean, default false).

### Schwachstelle erkennen
- **Wurf**: WAH-Step + Rang vs. **max(MV, physische Rüstung)** des Ziels
- **Effekt**: Pro Erfolg +2 Schaden auf physische Angriffe (MELEE/RANGED, **nicht** SPELL_ATTACK) gegen dieses Ziel für `Rang` Runden
- **Kosten**: 1 Schaden (Überanstrengung); **konsumiert keine Hauptaktion** (`hasActedThisRound` bleibt unverändert)
- **Speicherung**: `ActiveEffect` auf dem Anwender mit neuem Feld `targetCombatantId` und `ModifierEntry { DAMAGE_STEP, ADD, +2×Erfolge, ON_DAMAGE_DEALT }`. Erneutes Wirken gegen dasselbe Ziel ersetzt den alten Effekt.
- **Modifier-Engine**: `ModifierAggregator.applyModifiers` ignoriert Effekte mit gesetztem `targetCombatantId`. Nur `CombatService.performAttack` wendet sie explizit an, gefiltert nach `defender.id == effect.targetCombatantId` und `actionType ∈ {MELEE_ATTACK, RANGED_ATTACK}`. Dadurch sind Spruchschäden automatisch ausgeschlossen (laufen über `SpellService`) und SPELL_ATTACK-Talente erhalten den Bonus ebenfalls nicht.
- **Endpoint**: `POST /api/combat/sessions/{id}/spot-armor-flaw` mit `SpotArmorFlawRequest`, gibt `SpotArmorFlawResult` zurück.
- **Flyway V14**: `active_effects.target_combatant_id` (BIGINT, nullable).

### Krallenhand
- **Krallenhand**: Verwandelt die Hände magisch in Klauen für den waffenlosen Kampf (STR-basiertes Talent).
- **Modellierung**: Wird automatisch als Equipment vom Typ `WEAPON` mit Flag `clawWeapon=true` angelegt, wenn das Talent dem Charakter hinzugefügt wird. `damageBonus = rank + 3` — entspricht der Krallenhandstufe `STR + Rang + 3` in Kombination mit der Standardformel `damageStep = STR + weaponBonus`.
- **Lifecycle**: `CharacterService.syncClawWeapon()` läuft in `addTalent`/`updateTalentRank`/`removeTalent` — bei Rang-Änderung wird der Schadensbonus angepasst, bei Talent-Entfernung verschwindet das Equipment.
- **Schutz**: Manuelle Löschung über `removeEquipment` ist gesperrt (`IllegalStateException` mit Hinweis auf Talent-Entfernung).
- **Karma auf Schaden**: `AttackActionRequest.spendKarmaForDamage` aktiviert einen zusätzlichen W6 (Stufe 4) auf den Schadenswurf. Nur erlaubt wenn die ausgewählte Waffe `clawWeapon=true` ist — sonst Backend-Fehler. Das Ergebnis wird als `damageKarmaRoll` in `CombatActionResult` zurückgegeben und im UI separat angezeigt.

### Passive / Reaction Talents
- **Standhaftigkeit**: Passiv. Bei Niederschlagsprobe: STR-Step + Talentrang statt reiner STR-Step.
- **Starrsinn**: Auto-Gegenprobe gegen Verspotten. WIL-Step + Rang vs. Verspotten-Ergebnis. Bei Erfolg: Effekt negiert.
- **Eiserner Wille**: Freie Aktion. WIL-Step + Rang vs. Zauberwurf des Angreifers (manuell eingegeben). Bei Erfolg: jüngster negativer Zaubereffekt entfernt.
- **Ausweichen**: Nach Treffer. DEX-Step + Rang vs. Angriffswurf. Kostet 1 Schaden.
- **Riposte**: Reaktion nach Nahkampftreffer. DEX-Step + Rang vs. Angriffswurf. Schaden wird gehalten in `pendingRiposteAttackTotal`; nach Riposte-Entscheid aufgelöst. Extraerfolge → Gegenangriff. Kostet 2 Schaden.
- **Tigersprung**: Freie Aktion (1×/Runde). Kein Wurf. Initiative += Rang. `tigersprungUsedThisRound`-Flag, reset by `nextRound()`. Kostet 1 Schaden.

### GM-Bedingungen (manuell aktiviert)
Vom Meister aktivierte Zustände (Bedingungen wie Position/Anzahl Angreifer sind nicht automatisch berechenbar). Endpoint `POST .../combatants/{cId}/gm-condition?type=&rounds=`, Service `CombatService.applyGmCondition`. Im Frontend als Preset-Buttons im GM-Effekt-Dialog (Ziel + „Runden" aus dem Dialog).
- **Toter Winkel** (`EFFECT_TOTER_WINKEL`): ActiveEffect −2 `PHYSICAL_DEFENSE` + −2 `SPELL_DEFENSE`. **Gegen ein Ziel im Toten Winkel sind keine aktiven Verteidigungstalente möglich** — `performAttack` setzt `defenderHasRiposte`/`defenderHasDodge` auf false, wenn `hasActiveEffect(defender, EFFECT_TOTER_WINKEL)`. Erneutes Anwenden ersetzt den Effekt (Refresh).
- **Bedrängt** (`EFFECT_BEDRAENGT`): ActiveEffect −2 auf `ATTACK_STEP` (Aktionsproben) + `PHYSICAL_DEFENSE` + `SPELL_DEFENSE`. **Überwältigt**: erneutes Anwenden ersetzt den Effekt und verstärkt alle drei Mali kumulativ um je −1 (−2 → −3 → −4 …). Kombiniert mit Toter Winkel stapeln sich die VK-Mali (Engine summiert).

## Character Sheet — Configurable Bonuses
- **Defense bonuses**: `physicalDefenseBonus`, `spellDefenseBonus`, `socialDefenseBonus` (int, default 0) on `GameCharacter` — added on top of the formula/override value in `ModifierAggregator`. Editable via +/− steppers in the "Verteidigungs-Boni" section on the Attribute tab.
- **Stat bonuses** (Flyway V30): `healthBonus`, `initiativeBonus`, `recoveryBonus` (int, default 0) on `GameCharacter`, edited via +/− steppers in the "Weitere Boni" section on the Attribute tab (`statBonusFields` in `CharacterSheetComponent`, same generic `adjustField`/`getDefenseBonus` path). Applied in **both** `getBaseValue`/`getBaseValueFromCharacter` of `ModifierAggregator`: `healthBonus` → added to `UNCONSCIOUSNESS_RATING` **and** `DEATH_RATING`; `initiativeBonus` → `INITIATIVE_STEP` (also in combat); `recoveryBonus` → `RECOVERY_STEP` (clamped via `Math.max(0, …)`). All may be negative. Field get/set wired in `CharacterService.getCurrentFieldValue`/`applyFieldValue`.
- **Armor initiative penalty**: `initiativePenalty` field on `Equipment` (int, default 0) — automatically subtracted from `INITIATIVE_STEP` in `ModifierAggregator`. Entered when adding armor; shown as orange badge.

## Equipment System

Equipment is stored as `Equipment` entities on `GameCharacter` (OneToMany, CascadeType.ALL, EAGER). Type is the `EquipmentType` enum: `WEAPON | ARMOR | SHIELD | POTION | AMULET | VERBANDSZEUG | GEAR`.

| Type | Relevant fields | Effect |
|---|---|---|
| WEAPON | `damageBonus`, `twoHanded`, `attackTalentName` | Shown on character sheet; used manually in combat. `twoHanded` → kein Schild (siehe unten). `attackTalentName` (Flyway V32, nullable) ordnet die Waffe einem Angriffstalent/-fertigkeit zu — im Angriffsdialog werden dann nur passende Waffen + unzugeordnete (null) angeboten (`attackWeaponsFor`). |
| ARMOR | `physicalArmor`, `mysticalArmor`, `initiativePenalty` | `initiativePenalty` subtracted from `INITIATIVE_STEP` via `ModifierAggregator` |
| SHIELD | `physicalDefenseBonus`, `mysticDefenseBonus`, `initiativePenalty`, `buckler`, `autoStowed` | Bonuses added to defenses via `ModifierAggregator` (nur wenn `active`) |
| POTION | `quantity`, `healStep`, `extraRecovery` | See Erholungsproben below |
| AMULET | `charged`, `amuletForSpell`, `amuletStepBonus`, `bloodMagicDamage` | Verzweiflungsschlag-Amulett — siehe unten |
| VERBANDSZEUG | `quantity` | Arzt-Verbrauchsgegenstand (1× pro Arztprobe) — siehe Arzt |
| GEAR | `probeBonusTalentName`, `probeBonusValue` | Sonstige Ausrüstung mit Probenbonus auf ein Talent/eine Fertigkeit (z.B. Leichte Stiefel +2 Heimlicher Schritt) |

The "Ausrüstung" tab on the character sheet has separate sections per type. The "Erholung" tab handles recovery tests and potions.

### Ein-/Zweihändige Waffen + Schild-Automatik
- **`twoHanded`** (WEAPON): Zweihandwaffe — kann nicht mit Schild geführt werden (Ausnahme Buckler).
- **`buckler`** (SHIELD): darf auch mit zweihändigen Waffen geführt werden.
- **`autoStowed`** (SHIELD): vom System wegen Zweihandwaffe automatisch abgelegt (markiert es für die automatische Wiederanlegung; manuell abgelegte Schilde haben dieses Flag nicht).
- **Mechanik** (`CombatService.applyTwoHandedShieldRule`, in `performAttack` vor dem Trefferzweig): Angriff mit Zweihandwaffe legt ein aktives Nicht-Buckler-Schild ab (`active=false`, `autoStowed=true`); Angriff mit Einhandwaffe legt ein `autoStowed`-Schild wieder an (`active=true`, `autoStowed=false`). Da `ModifierAggregator` Schilde nur bei `active==true` wertet, fällt der Verteidigungsbonus automatisch weg. Ergebnis-Felder `CombatActionResult.shieldStowedName` / `shieldRestoredName`. Manuelles Anlegen via `setEquipmentActive` löscht `autoStowed`. Nur `performAttack` (Hauptwaffenangriff); Nachtreten (waffenlos)/Zweitwaffe (Nebenhand) unberührt.
- **Flyway V27**: `two_handed`, `buckler`, `auto_stowed` auf `character_equipment`.

### Sonstige Ausrüstung (GEAR) — Probenbonus
- **`probeBonusTalentName`** + **`probeBonusValue`**: GEAR-Gegenstand gibt einen Bonus auf die Probe eines bestimmten Talents/einer Fertigkeit (Match per Name, `equalsIgnoreCase`). Beispiel **Leichte Stiefel**: `probeBonusTalentName="Heimlicher Schritt"`, `probeBonusValue=2`.
- **Mechanik** (`ProbeService.rollProbe`): Summe der passenden GEAR-Boni wird auf die Würfelstufe addiert (`+ equipmentBonus` neben Attribut-Step + Rang + bonusSteps − Wunden). `ProbeResult.equipmentBonus` zur Anzeige. Mehrere passende Gegenstände stapeln.
- **Frontend**: „Sonstige Ausrüstung"-Sektion im Ausrüstung-Tab (Schnell-Buttons „Leichte Stiefel" und „Schwimmkristall" + generisches Formular mit Talent/Fertigkeit-Auswahl); Probenergebnis zeigt „inkl. +X Ausrüstung".
- **Schwimmkristall**: GEAR-Schnellanlage `addSchwimmkristall()` — `probeBonusTalentName="Schwimmen"`, `probeBonusValue=3`, `description="Erlaubt Unterwasseratmung von Rang Minuten."` (Beschreibung wird in der GEAR-Liste angezeigt). **Schwimmen** (STÄ) gibt es sowohl als **Talent** (`migrateUtilityTalents()`, wie Klettern/Heimlicher Schritt) als auch als **Fertigkeit** (`seedSkills()` + idempotent `migrateSchwimmenSkill()`); der GEAR-Bonus matcht per Name und greift bei beiden.
- **Flyway V28**: `EquipmentType`-Constraint um `GEAR` erweitert + Spalten `probe_bonus_talent_name`, `probe_bonus_value`.

**API**: `POST /api/characters/{id}/equipment` (body: Equipment), `DELETE /api/characters/{id}/equipment/{equipmentId}`, `PATCH /api/characters/{id}/equipment/{equipmentId}?quantity=N`.

### Verzweiflungsschlag-Amulette (Typ AMULET)
- **Mechanik**: Vor dem Wurf ansagen (wie Karma). Jedes Amulett gibt **+6 Stufen** (`amuletStepBonus`, Standard 6) **entweder** auf den Angriffs-/Zauberwurf **oder** auf den Schadenswurf — pro Anwendung wählbar. Mehrere Amulette gleichzeitig tragbar und anwendbar.
- **Variante**: `amuletForSpell` — `false` = wirkt auf physische Angriffe (`performAttack`), `true` = wirkt auf Zauber (`SpellService.castSpell`). Falsche Kombination wirft `IllegalStateException`.
- **Blutmagie-Kosten**: `bloodMagicDamage` (Standard 3) reduziert dauerhaft **Bewusstlosigkeits- UND Todesschwelle**, solange getragen. Zentral in `ModifierAggregator.bloodMagicDamage(c)` (Summe über alle Equipment) — abgezogen in `UNCONSCIOUSNESS_RATING` und `DEATH_RATING` (beide Methoden), greift in Kampf und Anzeige. In `DerivedStats.bloodMagicDamage` zur Anzeige.
- **Aufladen**: Amulett wird nach Anwendung entladen (`charged=false`). `POST /api/characters/{id}/equipment/{equipmentId}/recharge-amulet` opfert eine Erholungsprobe: Wurf ≥ 3 → `charged=true`, Heilung verfällt; Wurf < 3 → heilt regulär, Amulett bleibt entladen. Verbraucht in beiden Fällen einen Erholungs-Slot. Gibt `AmuletRechargeResult` zurück.
- **Anwendung im Kampf**: `AttackActionRequest.amuletAttackIds` / `amuletDamageIds` (Equipment-IDs), `SpellCastRequest.amuletCastIds` / `amuletDamageIds`. Angriffs-/Zauber-Amulette werden beim Wurf entladen (auch bei Fehlschlag, da auf die Probe gesetzt). **Schaden-Amulette werden erst entladen, wenn Schaden tatsächlich angewendet wird** — bei Fehlschlag, erfolgreichem Ausweichen oder Riposte bleiben sie **geladen**. Mechanik: `collectAmulets(...)` validiert + bündelt ohne zu entladen; `applyAmulets(...)` = collect + sofort entladen (für Angriffs-/Zauber-Amulette). Bei ausstehendem Treffer (pending Ausweichen/Riposte) werden die Schaden-Amulett-IDs auf dem Verteidiger gemerkt (`pendingDamageAmuletIds` + `pendingDamageAmuletAttackerId`, Flyway V29) und in `resolveDodge`/`performRiposte` via `dischargePendingDamageAmulets(...)` nur entladen, wenn der Schaden landet; `nextRound` verwirft unaufgelöste Reservierungen (Amulett bleibt geladen). Entladung über `characterRepo.save` / `@Transactional` persistiert.
- **Defaults serverseitig erzwungen**: `CharacterService.addEquipment` setzt bei Typ AMULET `charged=true`, `amuletStepBonus=6` (falls ≤0), `bloodMagicDamage=3` (falls ≤0).
- **Flyway V24**: Equipment-Constraint um `AMULET` erweitert + 4 neue Spalten auf `character_equipment`.
- **Frontend**: Amulett-Sektion im "Ausrüstung"-Tab (Hinzufügen/Liste/Aufladen, Blutmagie-Badge). Im Kampf: Amulett-Toggles (`+6 Angriff` / `+6 Schaden` bzw. `+6 Zauberwurf` / `+6 Schaden`) im Angriffs- und Zauberdialog, nur geladene Amulette der passenden Art.

### Erholungsproben (Recovery Tests)
- **Roll**: ZÄH-Step − wounds (min 1). Result reduces `currentDamage`.
- **Daily limit** from ZÄH: 1–6→1, 7–12→2, 13–18→3, 19–24→4, 25+→5. Stored as `recoveryTestsRemaining` (Integer, null = full) on `GameCharacter`.
- **Wound penalty**: −1 step per unversorgter Wunde, clamped to minimum 1. Effektiv: `wounds − min(arztWoundsTreated, wounds)` — ärztlich **versorgte Wunden** (siehe Arzt, Modus WUNDE) zählen nicht; die Versorgung bleibt bestehen (wird nicht pro Probe verbraucht). Jede Erholungsprobe setzt außerdem `arztInjuryTreated=false` (Verletzungsbehandlung wieder frei).
- **Erholungstrank** (`extraRecovery=false`): +`healStep` bonus steps, consumes one daily slot.
- **Heiltrank** (`extraRecovery=true`): +`healStep` bonus steps, grants an extra test (ignores daily limit).
- **`healStep`** default 7 for both types (ED4 standard). Quantity decrements by 1 on use.
- **Karma auf Erholungsprobe**: `POST /api/characters/{id}/recovery-test?spendKarma=true` → bei berechtigter Disziplin und Karma > 0 wird 1 Karma abgezogen und ein **W6 (Stufe 4)** (`stepRollService.roll(4)`) auf den Erholungswurf addiert; Ergebnis als `RecoveryTestResult.karmaRoll`. Berechtigung (`CharacterService.canUseKarmaOnRecovery`): Disziplin → Mindestkreis — **Elementarist/Krieger/Luftpirat/Tiermeister/Waffenschmied ab Kreis 3, Kundschafter erst ab Kreis 5**. Frontend: Karma-Checkbox (`recoveryUseKarma`) auf der Erholung-Seite, sichtbar via `canUseKarmaRecovery()`.
- **Reset**: `POST /api/characters/{id}/recovery-test/reset` → sets `recoveryTestsRemaining = max` (new day).
- **Frontend**: Tab "Erholung" shows slot dots, "Erholungsprobe würfeln" button, "Neuer Tag" reset, potion list with Trinken-button. `lastRecovery` (type `RecoveryTestResult`) displays last result.

### Arzt (Physician) — zwei Behandlungsmodi
- **Wurf** (beide Modi): WAH-Step + Arzt-Rang vs. **festem Mindestwurf 5**. **Verbandszeug** des Heilers (`EquipmentType.VERBANDSZEUG`) wird **1× pro Probe verbraucht — auch bei Fehlschlag**. Ohne Verbandszeug/Arzt-Fertigkeit: `IllegalStateException`. Modus-Vorbedingungen werden **vor** dem Verbandszeug-Verbrauch geprüft.
- **Modus `VERLETZUNG`** (verlorene LP behandeln): nur wenn `currentDamage > 0` und `arztInjuryTreated=false` (**1× pro Erholungsprobe**; Flag wird von `performRecoveryTest` zurückgesetzt — bei Fehlschlag der Arztprobe bleibt es false, erneuter Versuch erlaubt). Erfolg: `pendingRecoveryBonus += Rang`, `arztInjuryTreated=true`.
- **Modus `WUNDE`** (Wundversorgung): nur wenn unversorgte Wunden existieren (`arztWoundsTreated < wounds`). Erfolg: `arztWoundsTreated++` — der **−1-Wundmalus dieser Wunde** ist bei Erholungsproben dauerhaft unterdrückt (Verband bleibt; Zähler wird beim Setzen der Wundenzahl via `applyFieldValue` und beim Lesen gekappt). Mehrfach anwendbar, bis alle Wunden versorgt sind.
- **Endpoint**: `POST /api/characters/{id}/arzt` (Body `{ healerCharacterId, mode: VERLETZUNG|WUNDE }`, mode default VERLETZUNG), gibt `ArztResult` (inkl. `mode`, `woundsTreated`, `verbandszeugRemaining`).
- **Flyway V26** (`VERBANDSZEUG`-Typ) + **V34** (`arzt_wounds_treated` int, `arzt_injury_treated` boolean; altes `arzt_wound_penalty_negated` entfernt).
- **Frontend**: „Arzt"-Tab mit zwei Buttons („Verletzungen behandeln" / „Wunde versorgen", via `canTreatInjury()`/`canTreatWound()` gesperrt), Status-Banner (behandelt/versorgt x/y); „Erholung"-Tab zeigt effektiven Wundabzug (`effectiveWoundPenalty()`).

## Core Architecture: Modifier Engine
Every bonus/penalty goes through `ModifierAggregator`. Modifiers have:
- `targetStat`: which stat is modified (PHYSICAL_DEFENSE, ATTACK_STEP, INITIATIVE_STEP, MYSTIC_ARMOR, etc.)
- `operation`: ADD | MULTIPLY | OVERRIDE | SET_MIN | SET_MAX
- `triggerContext`: ALWAYS | ON_MELEE_ATTACK | ON_RANGED_ATTACK | ON_MELEE_DEFENSE | ON_RANGED_DEFENSE | ON_SOCIAL_ACTION | ON_INITIATIVE | ON_DAMAGE_RECEIVED | ON_DAMAGE_DEALT
- `value`: numeric value

Talents, spells, conditions, equipment, stances = all stored as `ActiveEffect` with `ModifierEntry` list on `CombatantState`.

## Package Structure
```
com.earthdawn
├── config/       WebSocketConfig, CorsConfig, JacksonConfig
├── controller/   CharacterController, CombatController, DiceController,
│               ReferenceDataController, UserAccountController
├── dto/          RollResult, DieRollDetail, ProbeRequest/Result,
│                 AttackActionRequest, CombatActionResult,
│                 DodgeRequest/Result, StandUpResult, KnockdownResult,
│                 FreeActionRequest/Result,
│                 TauntRequest/Result, DistractRequest/Result,
│                 AcrobaticDefenseResult, CombatSenseRequest/Result,
│                 IronWillResult, ManoeuverRequest/Result,
│                 RiposteRequest/Result, TigersprungResult,
│                 ZweitwaffeRequest,
│                 ThreadweaveRequest/Result, SpellCastRequest/Result,
│                 DerivedStats, FieldUpdateRequest
├── model/        GameCharacter, DisciplineDefinition, TalentDefinition, SkillDefinition,
│                 CharacterTalent, CharacterSkill, CharacterSpell, Equipment,
│                 ModifierEntry, ActiveEffect, UserAccount,
│                 SpellDefinition,
│                 CombatSession, CombatantState, CombatLog
│   └── enums/    StatType, AttributeType, ModifierOperation, TriggerContext, SourceType,
│                 ActionType, CombatStatus, CombatPhase,
│                 DeclaredStance, DeclaredActionType,
│                 SpellEffectType, FreeActionTarget, EquipmentType, Race
├── repository/   CharacterRepository, CombatSessionRepository, DisciplineRepository,
│                 TalentDefinitionRepository, SkillDefinitionRepository,
│                 SpellDefinitionRepository, CharacterSpellRepository,
│                 EquipmentRepository, UserAccountRepository
└── service/      StepRollService, ModifierAggregator, CharacterService,
                  CombatService, SpellService, ProbeService, DataInitializer
```

## Frontend Routes
```
/              → redirect to /characters
/characters    → CharacterListComponent
/characters/:id → CharacterSheetComponent (talent tests, dice roller, karma)
/combat        → CombatListComponent
/combat/:id    → CombatTrackerComponent (live via WebSocket)
/dice          → DiceRollerComponent
```

## API Endpoints
```
GET/POST/PUT/DELETE /api/characters/{id}
PATCH  /api/characters/{id}/field        { field, delta, absoluteValue }
PATCH  /api/characters/{id}/notes
GET    /api/characters/{id}/derived
POST   /api/characters/{id}/recalculate
POST   /api/characters/{id}/talents      ?talentDefinitionId=&rank=
POST   /api/characters/{id}/skills       ?skillDefinitionId=&rank=
POST   /api/characters/{id}/equipment    Equipment body
PATCH  /api/characters/{id}/equipment/{equipmentId}  ?quantity=N
DELETE /api/characters/{id}/equipment/{equipmentId}
POST   /api/characters/{id}/equipment/{equipmentId}/recharge-amulet → opfert Erholungsprobe (≥3 lädt Amulett), gibt AmuletRechargeResult
POST   /api/characters/{id}/holzhaut     → würfelt ZÄH-Step + Rang, speichert als holzhautBonus (überschreibt)
POST   /api/characters/{id}/holzhaut/end → reduziert currentDamage um Bonus, setzt Bonus auf 0
POST   /api/characters/{id}/recovery-test         ?spendKarma=true|false → Erholungsprobe würfeln (optionaler Karmawürfel bei berechtigter Disziplin ab Kreis 3/5); gibt RecoveryTestResult
POST   /api/characters/{id}/recovery-test/reset   → Neuer Tag: setzt recoveryTestsRemaining auf Max (aus ZÄH)
POST   /api/characters/{id}/arzt                  { healerCharacterId, mode: VERLETZUNG|WUNDE } → Arztprobe (MW 5, verbraucht 1 Verbandszeug); gibt ArztResult

GET    /api/reference/disciplines
GET    /api/reference/talents
GET    /api/reference/skills
GET    /api/reference/spells             ?discipline=
GET    /api/reference/spells/{id}

POST   /api/dice/roll        { step }
POST   /api/dice/probe       ProbeRequest { characterId, talentId|skillId, targetNumber, bonusSteps, spendKarma }

GET    /api/combat/sessions
POST   /api/combat/sessions              { name }
GET    /api/combat/sessions/{id}
DELETE /api/combat/sessions/{id}
POST   /api/combat/sessions/{id}/combatants         ?characterId=&isNpc=  (auch mitten im Kampf: in ACTION wird Initiative gewürfelt + einsortiert, in DECLARATION muss der Neuzugang noch ansagen)
DELETE /api/combat/sessions/{id}/combatants/{cId}

POST   /api/combat/sessions/{id}/initiative          → SETUP→ACTIVE, starts round 1 in DECLARATION phase
POST   /api/combat/sessions/{id}/next-round          → increments round, clears stances, enters DECLARATION

POST   /api/combat/sessions/{id}/combatants/{cId}/declare    ?stance=NONE|AGGRESSIVE|DEFENSIVE&actionType=WEAPON|SPELL
POST   /api/combat/sessions/{id}/combatants/{cId}/undeclare  → undo declaration (change allowed)
POST   /api/combat/sessions/{id}/combatants/{cId}/karma-initiative  ?spend=true|false  → Karma-auf-Initiative wählen (Ansagephase, Disziplin ab Kreis 3)

POST   /api/combat/sessions/{id}/attack              AttackActionRequest
POST   /api/combat/sessions/{id}/dodge               DodgeRequest
POST   /api/combat/sessions/{id}/free-action         FreeActionRequest
POST   /api/combat/sessions/{id}/taunt               TauntRequest
POST   /api/combat/sessions/{id}/fear                FearRequest { actorCombatantId, targetCombatantId, bonusSteps, spendKarma } — Verängstigen (WIL vs. MV)
POST   /api/combat/sessions/{id}/combatants/{cId}/resist-fear   → WIL-Widerstandsprobe gegen Verängstigt (1×/Runde)
POST   /api/combat/sessions/{id}/combatants/{cId}/neutralize-magic/open → broadcastet den Auswahldialog (NEUTRALIZE_MAGIC_SELECT) an alle Clients
POST   /api/combat/sessions/{id}/neutralize-magic    NeutralizeMagicRequest { actorCombatantId, targetCombatantId, effectId, effectLevel, bonusSteps, spendKarma }
POST   /api/combat/sessions/{id}/distract            DistractRequest
POST   /api/combat/sessions/{id}/spot-armor-flaw     SpotArmorFlawRequest
POST   /api/combat/sessions/{id}/combat-sense        CombatSenseRequest
POST   /api/combat/sessions/{id}/combatants/{cId}/stand-up
POST   /api/combat/sessions/{id}/combatants/{cId}/aufspringen    ?spendKarma=
POST   /api/combat/sessions/{id}/combatants/{cId}/acrobatic-defense  ?bonusSteps=&spendKarma=
POST   /api/combat/sessions/{id}/combatants/{cId}/iron-will      ?attackTotal=&spendKarma=
POST   /api/combat/sessions/{id}/riposte             RiposteRequest { defenderCombatantId, bonusSteps, spendKarma, riposteAttempted }
POST   /api/combat/sessions/{id}/manoeuver           ManoeuverRequest { actorCombatantId, targetCombatantId, bonusSteps, spendKarma }
POST   /api/combat/sessions/{id}/zweitwaffe          ZweitwaffeRequest { actorCombatantId, defenderCombatantId, weaponId?, bonusSteps, spendKarma }
POST   /api/combat/sessions/{id}/nachtreten          NachtretenRequest { actorCombatantId, defenderCombatantId, bonusSteps, spendKarma } — waffenloser Zusatzangriff, nur vs. niedrigere Initiative
POST   /api/combat/sessions/{id}/schwanzangriff       SchwanzangriffRequest { actorCombatantId, defenderCombatantId, weaponId?, bonusSteps, spendKarma } — T'skrang-Schwanzangriff, −2 auf alle Proben der Runde
POST   /api/combat/sessions/{id}/combatants/{cId}/tigersprung   → no body; initiative += rank, costs 1 damage
POST   /api/combat/sessions/{id}/combatants/{cId}/lufttanz      → no body; +rank initiative (DECLARATION), enables bonus melee attack, costs 2 damage
POST   /api/combat/sessions/{id}/lufttanz-attack                LufttanzAttackRequest (bonus melee attack, no hasActedThisRound consumed)
POST   /api/combat/sessions/{id}/combatants/{cId}/blattschuss-add-karma → rollt +W6 auf pending Blattschuss-Angriff

PATCH  /api/combat/sessions/{id}/combatants/{cId}/value  ?field=damage|wounds|karma|initiative|defeated&delta=
POST   /api/combat/sessions/{id}/combatants/{cId}/effects
DELETE /api/combat/sessions/{id}/combatants/{cId}/effects/{effectId}
POST   /api/combat/sessions/{id}/combatants/{cId}/gm-condition  ?type=TOTER_WINKEL|BEDRAENGT&rounds=  → GM-Spezialbedingung anwenden
POST   /api/combat/sessions/{id}/combatants/{cId}/combat-option  ?option=USE_ACTION

POST   /api/combat/sessions/{id}/weave-thread        ThreadweaveRequest
POST   /api/combat/sessions/{id}/cast-spell          SpellCastRequest
POST   /api/combat/sessions/{id}/combatants/{cId}/cancel-spell

GET    /api/combat/sessions/{id}/log
POST   /api/combat/sessions/{id}/end
```

## WebSocket
- Endpoint: `/ws` (SockJS)
- Topic: `/topic/combat/{sessionId}` — broadcasts full `CombatSession` on every state change
- Frontend subscribes in `CombatTrackerComponent` via `WebSocketService`
- **Kampfprotokoll**: `logEntries` werden im Frontend **absteigend** (neueste oben) angezeigt (`toLogEntries`). Angriffe schreiben strukturierte Wurf-/Modifikator-Details nach `CombatLog.rollDetailsJson` (`attackLogJson`): Angriffs- und Schadenswurf (Einzelwürfel, Karmawürfel, Total), Strain (Überanstrengung) und Modifikatoren (`attackBonusNotes`/`damageBonusNotes`). Das Frontend parst `rollDetailsJson` und rendert eine Detailzeile pro Eintrag.
- **Synchronisierte Modale** (`broadcastWithModal` → `liveModal` → Frontend `openLocalModalForType`): u.a. `ATTACK_RESULT`, `INITIATIVE`, `DODGE`, `RIPOSTE`, `TAUNT`, `MANOEUVER`, `FEAR`, `FEAR_RESIST`, `NEUTRALIZE_MAGIC` sowie **`NEUTRALIZE_MAGIC_SELECT`** — bislang das einzige *interaktive* Broadcast-Modal (Auswahldialog statt Ergebnis; jeder Client rendert die Effektliste lokal, wer bestätigt, löst aus) — sowie **`COMBAT_ENDED`** — `endCombat()` broadcastet ein „Kampf beendet"-Modal an alle Clients (Payload `name`/`round`). Im Tracker zusätzlich ein persistentes „🏁 Kampf beendet"-Badge bei `status === 'FINISHED'`.

## Reference Data (DataInitializer)
Seeded automatically (idempotent) on first start via migration methods in `migrateDodgeTalent()` and `migrateFreeActionTalents()`:

**Disciplines (12):** Krieger, Pfadsucher, Dieb, Illusionist, Elementarist, Magier, Geisterbeschwörer, Nekromant, Schwertkämpfer, Troubadour, Bogenschütze, Waffenmeister

**Talents seeded:**
- Attack talents: Nahkampfwaffen, Projektilwaffen, Wurfwaffen, Waffenloser Kampf, Spruchzauberei
- Weaving: Elementarismus, Illusionismus, Magie, Geisterbeschwörung
- Free action: Magische Markierung (PER, freeAction=true)
- Reaction / passive: Ausweichen, Standhaftigkeit, Starrsinn, Eiserner Wille, Riposte (DEX)
- Social actions: Verspotten (CHA), Ablenken (CHA)
- Defensive actions: Akrobatische Verteidigung (DEX), Kampfsinn (PER), Manövrieren (DEX)
- Recon: Schwachstelle erkennen (PER, ziel-spezifischer Schadensbonus, nur physisch)
- Schützen: Blattschuss (PER, bis zu Rang Karmawürfel auf Fernkampf-Probe — auch nachträglich nach Fehlschlag)
- Additional attacks: Zweitwaffe (DEX), Nachtreten (DEX, waffenlos, nur vs. niedrigere Initiative)
- Initiative: Tigersprung (DEX, once/round, no roll), Lufttanz (DEX, +rank Initiative + Bonus-Nahkampfangriff bei Init-Vorsprung ≥10)
- Charaktertalente (außerhalb Kampf): Holzhaut (ZÄH), Krallenhand (STR — auto-managed Equipment mit `clawWeapon=true`)
- Matrizen: Zaubermatritze (PER, `maxInstances=3`, `rankFromCircle`), **Erweiterte Matrize** (PER, `maxInstances=3`, `rankFromCircle`) — siehe unten

### Zaubermatrizen — Erweiterte Matrize
- **Zaubermatritze** und **Erweiterte Matrize** halten je einen zugewiesenen Zauber (`CharacterTalent.assignedSpell`), mehrfach lernbar (`maxInstances=3`), Rang = Kreis (`rankFromCircle`).
- **Erweiterte Matrize**: Liegt ein Zauber in der Matrize, gilt **ein Faden als bereits gewoben** → effektiver Fadenweben-Aufwand = `max(0, spell.threads − 1)`.
  - `SpellService.weaveThread`: beim Vorbereitungsstart `threadsRequired = max(0, threads − discount)` (discount 1, wenn der Zauber in einer Erweiterten Matrize liegt — `isInErweiterteMatrize`).
  - `SpellService.castSpell`: ist der effektive Bedarf 0 (z.B. 1-Faden-Zauber in Erweiterter Matrize), kann **direkt ohne Vorbereitung** gewirkt werden.
  - Frontend: `spellMatrices()` listet beide Typen; erweiterte Matrizen zeigen „1 Faden vorgewoben" und „noch N Fäden" (`matrixRemainingThreads`).
  - `CharacterService.assignSpellToMatrix` akzeptiert **beide** Matrix-Typen (ZAUBERMATRITZE + ERWEITERTE_MATRIZE).
  - Keine Migration (nutzt bestehendes `assignedSpell`); Seeding idempotent in `migrateFreeActionTalents()`.

**Skills seeded (Auswahl):** Reiten, diverse Wissens-/Handwerks-/Sozialfertigkeiten, Arzt (PER), sowie die **Waffen-Fertigkeiten Nahkampfwaffen & Projektilwaffen** (DEX, Kategorie „Waffen"). Idempotent via `migrateWeaponSkills()` / `migrateArztSkill()`.

**Waffen-Fertigkeiten im Kampf:** Nahkampfwaffen/Projektilwaffen existieren sowohl als Talent **als auch** als Fertigkeit. Im Angriffsdialog wählbar als Angriffsbasis (`AttackActionRequest.skillId` statt `talentId`). Mechanik identisch zum Talent (GES-Step + Rang vs. KV), **aber Karma ist nicht erlaubt** — `performAttack` rollt kein Karma, wenn `skillId != null` (auch wenn `spendKarma=true`); im Frontend wird der Karma-Toggle ausgeblendet.

**Spells (~105 total):** Illusionist (Circles 1–8) + Geisterbeschwörer (Circles 1–8)

## Key Implementation Notes
- **Karma die**: Always Step 4 = W6 for all disciplines. `diceService.roll(4)` everywhere. **Do not use** `roll(6)` (that is Step 6 = W10).
- **Stance effects**: Applied as `ActiveEffect` with `ATTACK_STEP` modifier (not via request flags). Aggressive adds `+3 ATTACK_STEP` + `-3` to all defenses; Defensive adds `-3 ATTACK_STEP` + `+3` to all defenses. Both cleared at round end in `nextRound()`.
- **CombatPhase**: `DECLARATION` (all declare before initiative) → `ACTION` (fight). Phase stored on `CombatSession`. Auto-transitions to `ACTION` when all non-defeated combatants have declared.
- **Successes formula**: `successes = 1 + floor((total − TN) / 5)` when successful. Used for Verspotten, Ablenken, Akrobatische Verteidigung, Kampfsinn.
- **Knockdown removes Akrobatische Verteidigung**: In `performKnockdownCheck()`, when `knocked = true`, the effect is explicitly removed using `TalentNames.AKROBATISCHE_VERTEIDIGUNG`.
- **Kampfsinn initiative check**: `actor.getInitiativeOrder() < target.getInitiativeOrder()` (lower order = higher initiative, since combatants are sorted descending).
- **Eiserner Wille removes SPELL-sourced effects**: On success, removes the most recently added `ActiveEffect` with `sourceType == SourceType.SPELL` and `negative == true` from the actor.
- **SpellService** uses `ModifierAggregator` for `KARMA_STEP` and spell defense values — same engine as physical combat.
- **Free actions**: `TalentDefinition.freeAction=true` triggers `performFreeAction()` in `CombatService`. Does not set `hasActedThisRound`. Eiserner Wille is a separate endpoint (not via the generic free-action path) because it needs a manual `attackTotal` input.
- **TalentNames constants**: All hardcoded talent/effect name strings live in `model/TalentNames.java`. Use these constants instead of inline German strings when checking for specific talents or effects.
- **Riposte pending state**: `performAttack()` detects a Riposte trigger and stores `pendingRiposteAttackTotal` + `pendingRiposteAttackerId` on the defender instead of applying damage. The frontend shows a Riposte button only when `pendingRiposteAttackTotal >= 0`. The `/riposte` endpoint resolves it (accept damage or counter-attack).
- **Once-per-round flags**: `tigersprungUsedThisRound` and `zweitWaffeUsedThisRound` on `CombatantState` are independent of `hasActedThisRound` and are reset in `nextRound()`.
