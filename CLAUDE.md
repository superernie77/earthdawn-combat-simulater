# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
| **Ablenken** | CHA | Soziale VK (SV) | −successes KV auf Anwender UND Ziel (Toter Winkel für Verbündete). |
| **Akrobatische Verteidigung** | DEX | Höchste KV aller Gegner | +2 KV/Erfolg für 1 Runde. Erlischt bei Niedergeschlagen. Nicht mit Kampfsinn. |
| **Kampfsinn** | PER | MV des Ziels | +2 KV + +2 Angriff/Erfolg für 1 Runde. Nur vs. Ziele mit niedrigerer Initiative. Nicht mit Akrobatischer Verteidigung. |
| **Manövrieren** | DEX | KV des Ziels | +successes×2 KV (ON_MELEE_DEFENSE) + pending attack bonus für 1 Runde. |
| **Zweitwaffe** | DEX | KV des Ziels | Zusätzlicher Waffenangriff. Eigener once-per-round-Flag (`zweitWaffeUsedThisRound`). Kann nach Hauptangriff oder statt ihm eingesetzt werden. |
| **Nachtreten** | DEX | KV des Ziels | Zusätzlicher **waffenloser** Angriff (Einfache Aktion). Schaden = reine STR-Stufe (+2/Übererfolg), kein Waffenbonus. Eigener once-per-round-Flag (`nachtretenUsedThisRound`), verbraucht **kein** `hasActedThisRound`. **Nur gegen Ziele mit niedrigerer Initiative** (`attacker.initiative > defender.initiative`). Riposte/Ausweichen des Ziels wie beim Nahkampf. Endpoint `POST /sessions/{id}/nachtreten` (`NachtretenRequest`). Flyway V25. |

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
- **Bonus-Trigger**: In `performAttack` nach erfolgreichem `MELEE_ATTACK`: wenn `attacker.initiative - defender.initiative ≥ 10` und Lufttanz aktiviert und Bonus noch nicht verbraucht → `pendingLufttanzTargetId` + `pendingLufttanzWeaponId` werden auf `attacker` gesetzt. `CombatActionResult.lufttanzBonusReady = true` und `lufttanzInitiativeDiff` zur Anzeige.
- **Bonus-Angriff**: `POST /api/combat/sessions/{id}/lufttanz-attack` (`LufttanzAttackRequest`). Führt regulären `performAttack` mit gespeichertem Ziel und Waffe aus, verbraucht **kein** `hasActedThisRound`, setzt `lufttanzBonusUsedThisRound=true` und löscht pending-Felder vor dem Aufruf — verhindert dadurch Selbst-Retrigger.
- **CombatantState** Felder: `lufttanzActivatedThisRound`, `lufttanzBonusUsedThisRound`, `pendingLufttanzTargetId` (-1 = none), `pendingLufttanzWeaponId` (-1). Alle 4 werden in `nextRound()` zurückgesetzt.
- **Flyway V15**: 4 neue Spalten auf `combatant_states`.

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

## Character Sheet — Configurable Bonuses
- **Defense bonuses**: `physicalDefenseBonus`, `spellDefenseBonus`, `socialDefenseBonus` (int, default 0) on `GameCharacter` — added on top of the formula/override value in `ModifierAggregator`. Editable via +/− steppers in the "Verteidigungs-Boni" section on the Attribute tab.
- **Armor initiative penalty**: `initiativePenalty` field on `Equipment` (int, default 0) — automatically subtracted from `INITIATIVE_STEP` in `ModifierAggregator`. Entered when adding armor; shown as orange badge.

## Equipment System

Equipment is stored as `Equipment` entities on `GameCharacter` (OneToMany, CascadeType.ALL, EAGER). Type is the `EquipmentType` enum: `WEAPON | ARMOR | SHIELD | POTION | AMULET | VERBANDSZEUG | GEAR`.

| Type | Relevant fields | Effect |
|---|---|---|
| WEAPON | `damageBonus`, `twoHanded` | Shown on character sheet; used manually in combat. `twoHanded` → kein Schild (siehe unten) |
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
- **Frontend**: „Sonstige Ausrüstung"-Sektion im Ausrüstung-Tab (Schnell-Button „Leichte Stiefel" + generisches Formular mit Talent/Fertigkeit-Auswahl); Probenergebnis zeigt „inkl. +X Ausrüstung".
- **Flyway V28**: `EquipmentType`-Constraint um `GEAR` erweitert + Spalten `probe_bonus_talent_name`, `probe_bonus_value`.

**API**: `POST /api/characters/{id}/equipment` (body: Equipment), `DELETE /api/characters/{id}/equipment/{equipmentId}`, `PATCH /api/characters/{id}/equipment/{equipmentId}?quantity=N`.

### Verzweiflungsschlag-Amulette (Typ AMULET)
- **Mechanik**: Vor dem Wurf ansagen (wie Karma). Jedes Amulett gibt **+6 Stufen** (`amuletStepBonus`, Standard 6) **entweder** auf den Angriffs-/Zauberwurf **oder** auf den Schadenswurf — pro Anwendung wählbar. Mehrere Amulette gleichzeitig tragbar und anwendbar.
- **Variante**: `amuletForSpell` — `false` = wirkt auf physische Angriffe (`performAttack`), `true` = wirkt auf Zauber (`SpellService.castSpell`). Falsche Kombination wirft `IllegalStateException`.
- **Blutmagie-Kosten**: `bloodMagicDamage` (Standard 3) reduziert dauerhaft **Bewusstlosigkeits- UND Todesschwelle**, solange getragen. Zentral in `ModifierAggregator.bloodMagicDamage(c)` (Summe über alle Equipment) — abgezogen in `UNCONSCIOUSNESS_RATING` und `DEATH_RATING` (beide Methoden), greift in Kampf und Anzeige. In `DerivedStats.bloodMagicDamage` zur Anzeige.
- **Aufladen**: Amulett wird nach Anwendung entladen (`charged=false`). `POST /api/characters/{id}/equipment/{equipmentId}/recharge-amulet` opfert eine Erholungsprobe: Wurf ≥ 3 → `charged=true`, Heilung verfällt; Wurf < 3 → heilt regulär, Amulett bleibt entladen. Verbraucht in beiden Fällen einen Erholungs-Slot. Gibt `AmuletRechargeResult` zurück.
- **Anwendung im Kampf**: `AttackActionRequest.amuletAttackIds` / `amuletDamageIds` (Equipment-IDs), `SpellCastRequest.amuletCastIds` / `amuletDamageIds`. Angriffs-/Zauber-Amulette werden beim Wurf entladen (auch bei Fehlschlag), Schadens-Amulette nur bei Treffer (Schadenswurf). Geteilte Hilfsmethode: `CombatService.applyAmulets(...)`. Entladung wird über `characterRepo.save` / `@Transactional` persistiert.
- **Defaults serverseitig erzwungen**: `CharacterService.addEquipment` setzt bei Typ AMULET `charged=true`, `amuletStepBonus=6` (falls ≤0), `bloodMagicDamage=3` (falls ≤0).
- **Flyway V24**: Equipment-Constraint um `AMULET` erweitert + 4 neue Spalten auf `character_equipment`.
- **Frontend**: Amulett-Sektion im "Ausrüstung"-Tab (Hinzufügen/Liste/Aufladen, Blutmagie-Badge). Im Kampf: Amulett-Toggles (`+6 Angriff` / `+6 Schaden` bzw. `+6 Zauberwurf` / `+6 Schaden`) im Angriffs- und Zauberdialog, nur geladene Amulette der passenden Art.

### Erholungsproben (Recovery Tests)
- **Roll**: ZÄH-Step − wounds (min 1). Result reduces `currentDamage`.
- **Daily limit** from ZÄH: 1–6→1, 7–12→2, 13–18→3, 19–24→4, 25+→5. Stored as `recoveryTestsRemaining` (Integer, null = full) on `GameCharacter`.
- **Wound penalty**: −1 step per wound, clamped to minimum 1. **Ausnahme**: ist `arztWoundPenaltyNegated=true` (nach erfolgreicher Arztbehandlung), entfällt der Wundabzug für **eine** Erholungsprobe; das Flag wird dabei verbraucht.
- **Erholungstrank** (`extraRecovery=false`): +`healStep` bonus steps, consumes one daily slot.
- **Heiltrank** (`extraRecovery=true`): +`healStep` bonus steps, grants an extra test (ignores daily limit).
- **`healStep`** default 7 for both types (ED4 standard). Quantity decrements by 1 on use.
- **Reset**: `POST /api/characters/{id}/recovery-test/reset` → sets `recoveryTestsRemaining = max` (new day).
- **Frontend**: Tab "Erholung" shows slot dots, "Erholungsprobe würfeln" button, "Neuer Tag" reset, potion list with Trinken-button. `lastRecovery` (type `RecoveryTestResult`) displays last result.

### Arzt (Physician) — Verletzungen und Wunden
- **Wurf**: WAH-Step + Arzt-Rang vs. **festem Mindestwurf 5** (ED4-DN für „Verletzungen und Wunden"; **nicht** 6×Wunden). Nur möglich, wenn der Patient ≥1 Wunde hat.
- **Verbandszeug**: Verbrauchsgegenstand des Heilers (`EquipmentType.VERBANDSZEUG`, `quantity` = Anwendungen). **1× pro Arztprobe verbraucht** (auch bei Fehlschlag). Ohne Verbandszeug wirft die Probe `IllegalStateException`.
- **Erfolg**: (1) Patient erhält **+Rang** als `pendingRecoveryBonus` auf die nächste Erholungsprobe; (2) `arztWoundPenaltyNegated=true` → die nächste Erholungsprobe ignoriert den Wundabzug (Wunde bleibt, aber ohne Recovery-Malus).
- **Endpoint**: `POST /api/characters/{id}/arzt` (Body `{ healerCharacterId }`), gibt `ArztResult` (inkl. `woundPenaltyNegated`, `verbandszeugRemaining`).
- **Flyway V26**: `EquipmentType`-Constraint um `VERBANDSZEUG` erweitert + Spalte `arzt_wound_penalty_negated` auf `characters`.
- **Frontend**: Verbandszeug-Sektion im „Ausrüstung"-Tab (Mengensteuerung). „Arzt"-Tab zeigt Verbandszeug-Bestand des Heilers, sperrt die Probe ohne Verbandszeug; „Erholung"-Tab zeigt aktive Wundpflege.

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
POST   /api/characters/{id}/recovery-test         { potionId? } → Erholungsprobe würfeln (optionaler Trank-Bonus); gibt RecoveryTestResult
POST   /api/characters/{id}/recovery-test/reset   → Neuer Tag: setzt recoveryTestsRemaining auf Max (aus ZÄH)
POST   /api/characters/{id}/arzt                  { healerCharacterId } → Arztprobe (MW 5, verbraucht 1 Verbandszeug); gibt ArztResult

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
POST   /api/combat/sessions/{id}/combatants         ?characterId=&isNpc=
DELETE /api/combat/sessions/{id}/combatants/{cId}

POST   /api/combat/sessions/{id}/initiative          → SETUP→ACTIVE, starts round 1 in DECLARATION phase
POST   /api/combat/sessions/{id}/next-round          → increments round, clears stances, enters DECLARATION

POST   /api/combat/sessions/{id}/combatants/{cId}/declare    ?stance=NONE|AGGRESSIVE|DEFENSIVE&actionType=WEAPON|SPELL
POST   /api/combat/sessions/{id}/combatants/{cId}/undeclare  → undo declaration (change allowed)

POST   /api/combat/sessions/{id}/attack              AttackActionRequest
POST   /api/combat/sessions/{id}/dodge               DodgeRequest
POST   /api/combat/sessions/{id}/free-action         FreeActionRequest
POST   /api/combat/sessions/{id}/taunt               TauntRequest
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
POST   /api/combat/sessions/{id}/combatants/{cId}/tigersprung   → no body; initiative += rank, costs 1 damage
POST   /api/combat/sessions/{id}/combatants/{cId}/lufttanz      → no body; +rank initiative (DECLARATION), enables bonus melee attack, costs 2 damage
POST   /api/combat/sessions/{id}/lufttanz-attack                LufttanzAttackRequest (bonus melee attack, no hasActedThisRound consumed)
POST   /api/combat/sessions/{id}/combatants/{cId}/blattschuss-add-karma → rollt +W6 auf pending Blattschuss-Angriff

PATCH  /api/combat/sessions/{id}/combatants/{cId}/value  ?field=damage|wounds|karma|initiative|defeated&delta=
POST   /api/combat/sessions/{id}/combatants/{cId}/effects
DELETE /api/combat/sessions/{id}/combatants/{cId}/effects/{effectId}
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
  - Frontend: `spellMatrices()` listet beide Typen; erweiterte Matrizen zeigen „1 Faden vorgewoben" und „noch N Fäden".
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
