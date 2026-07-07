# Earthdawn Combat Simulator

A local combat tracker for the **Earthdawn 4th Edition (FASA)** pen-and-paper RPG. Manages characters, combat sessions, and resolves all combat mechanics according to ED4 rules — including the step/dice system, exploding dice, wounds, active effects, spells, and free actions.

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Getting Started](#getting-started)
3. [Architecture Overview](#architecture-overview)
4. [Data Model](#data-model)
5. [Modifier Engine](#modifier-engine)
6. [ED4 Dice & Step System](#ed4-dice--step-system)
7. [Combat Flow](#combat-flow)
8. [Spell System](#spell-system)
9. [Free Actions](#free-actions)
10. [Main-Action Combat Talents](#main-action-combat-talents)
11. [Passive / Reaction Talents](#passive--reaction-talents)
12. [API Reference](#api-reference)
13. [WebSocket](#websocket)
14. [Frontend Structure](#frontend-structure)
15. [Reference Data](#reference-data)
16. [Versioning](#versioning)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Spring WebSocket (STOMP) |
| Frontend | Angular 20 standalone components, Angular Material dark theme |
| Database | PostgreSQL (via Docker) |
| Real-time | WebSocket over SockJS/STOMP — full session state pushed on every change |
| Build | Maven (`./mvnw`), Angular CLI (`npx ng`) |

---

## Getting Started

### Prerequisites
- Java 21+
- Node.js 20+
- Docker (for PostgreSQL)

### 1. Start the database
```bash
docker compose up -d
```
> ⚠️ Modern Docker installations ship Compose as a plugin — use `docker compose` (no hyphen). The standalone `docker-compose` binary is no longer installed by default.

### 2. Start the backend
```bash
cd backend
./mvnw spring-boot:run
```
Runs on `http://localhost:8081`. Reference data (disciplines, talents, spells) is seeded automatically on first start by `DataInitializer`.

### 3. Start the frontend
```bash
cd frontend
npm install
npx ng serve --open
```
Runs on `http://localhost:4200`.

---

## Production Deployment

The app ships with a `docker-compose.prod.yml` that builds backend + frontend images and routes traffic through Traefik.

### Deploy / update on the server
```bash
git pull && docker compose -f docker-compose.prod.yml up -d --build
```

`--build` forces a full image rebuild from source. Omit it to just restart without rebuilding.

> ⚠️ Use `docker compose` (plugin syntax, no hyphen). The old standalone `docker-compose` binary is not installed on modern Docker versions.

### View backend logs
```bash
docker compose -f docker-compose.prod.yml logs -f earthdawn-backend
```

Add `--tail 100` to start from only the last 100 lines:
```bash
docker compose -f docker-compose.prod.yml logs -f --tail 100 earthdawn-backend
```

---

## Architecture Overview

```
frontend (Angular 20)
    ↕ HTTP REST + WebSocket (STOMP)
backend (Spring Boot 3.5)
    ↕ Spring Data JPA
PostgreSQL (Docker)
```

**Backend package layout:**
```
com.earthdawn
├── config/        WebSocketConfig, CorsConfig, JacksonConfig
├── controller/    CharacterController, CombatController,
│                  DiceController, ReferenceDataController
├── dto/           All request/response objects (see API Reference)
├── model/         JPA entities
│   └── enums/     All enumerations
├── repository/    Spring Data JPA repositories
└── service/       Business logic
    ├── StepRollService      Dice rolling, step-to-dice table, explosion
    ├── ModifierAggregator   Central stat calculation engine
    ├── CombatService        All combat actions, round management
    ├── SpellService         Fadenweben + Spruchzauberei
    ├── CharacterService     Character CRUD, derived stats
    ├── ProbeService         Talent/Skill test rolls (character sheet)
    └── DataInitializer      Reference data seeding (idempotent)
```

---

## Data Model

### GameCharacter
Core entity. Attributes are stored as raw values (1–20); in ED4 FASA the attribute value equals the step number directly (no conversion table).

| Field | Type | Notes |
|---|---|---|
| name | String | |
| discipline | DisciplineDefinition | FK |
| circle | int | 1–15 |
| dexterity, strength, toughness, perception, willpower, charisma | int | Raw attribute = step number |
| currentDamage, wounds | int | Combat state |
| karmaCurrent, karmaMax | int | |
| talents | List\<CharacterTalent\> | Each with rank |
| skills | List\<CharacterSkill\> | Each with rank |
| equipment | List\<Equipment\> | Weapons, armor, shields |
| spells | List\<CharacterSpell\> | Known spells |

### DisciplineDefinition
| Field | Type | Notes |
|---|---|---|
| name | String | e.g. "Krieger", "Elementarist" |
| karmaStep | int | Always 4 (= W6) in practice |
| accessTalentNames | List\<String\> | Talent names available to this discipline |

### TalentDefinition
| Field | Type | Notes |
|---|---|---|
| name | String | Unique |
| attribute | AttributeType | Which attribute the talent uses |
| attackTalent | boolean | True for weapon/spell attack talents |
| testable | boolean | Can be rolled on the character sheet |
| rankScaled | boolean | If true, passive modifier value × rank |
| passiveModifiers | List\<ModifierEntry\> | Permanent stat bonuses granted by this talent |
| **freeAction** | boolean | Marks talent as a free combat action |
| freeActionTestStat | StatType | What defense to roll against |
| freeActionEffectTarget | FreeActionTarget | SELF or TARGET |
| freeActionModifyStat | StatType | What stat the effect modifies |
| freeActionTriggerContext | TriggerContext | When the modifier activates |
| freeActionValuePerSuccess | double | Modifier value per extra success |
| freeActionDuration | int | Effect duration in rounds |
| freeActionDamageCost | int | Damage taken by the user on activation |

### SpellDefinition
| Field | Type | Notes |
|---|---|---|
| name | String | |
| discipline | String | Discipline name |
| circle | int | Minimum circle to learn |
| threads | int | Fäden to weave before casting (0 = instant) |
| weavingDifficulty | int | Target number for each thread weave |
| castingDifficulty | int | Fixed cast TN (0 = target's spell defense) |
| effectType | SpellEffectType | DAMAGE, BUFF, DEBUFF, HEAL |
| effectStep | int | Damage/heal step |
| useMysticArmor | boolean | DAMAGE: mystic (true) vs physical armor |
| modifyStat | StatType | BUFF/DEBUFF: which stat |
| modifyOperation | ModifierOperation | ADD, MULTIPLY, etc. |
| modifyValue | double | Modifier amount |
| modifyTrigger | TriggerContext | When modifier is active |
| duration | int | Rounds (-1 = permanent) |

### CombatSession
| Field | Type | Notes |
|---|---|---|
| name | String | |
| round | int | Current round number |
| status | CombatStatus | SETUP → ACTIVE → FINISHED |
| phase | CombatPhase | DECLARATION → ACTION (resets each round) |
| combatants | List\<CombatantState\> | Ordered by initiativeOrder |
| log | List\<CombatLog\> | Full action history |

### CombatantState
Runtime combat state, independent of the persisted `GameCharacter`.

| Field | Type | Notes |
|---|---|---|
| character | GameCharacter | FK (read-only reference) |
| initiative | int | Rolled each round |
| initiativeOrder | int | Sort index (0 = highest init) |
| currentDamage | int | |
| wounds | int | Each wound = −1 step on all rolls |
| currentKarma | int | |
| hasActedThisRound | boolean | Reset on `nextRound()` |
| knockedDown | boolean | −3 on all tests and defenses |
| defeated | boolean | currentDamage ≥ unconsciousness rating |
| **hasDeclared** | boolean | Set during DECLARATION phase |
| **declaredStance** | DeclaredStance | NONE / AGGRESSIVE / DEFENSIVE |
| **declaredActionType** | DeclaredActionType | WEAPON / SPELL |
| preparingSpellId | Long | ID of spell being threaded |
| threadsWoven / threadsRequired | int | Thread progress |
| pendingDodgeDamage | int | Damage held until dodge resolved |
| **pendingRiposteAttackTotal** | int | Attack total awaiting Riposte resolution (−1 = none pending) |
| **pendingRiposteAttackerId** | Long | ID of the attacker for Riposte counter-attack |
| **tigersprungUsedThisRound** | boolean | Tigersprung already used this round; reset by `nextRound()` |
| **zweitWaffeUsedThisRound** | boolean | Zweitwaffe already used this round; reset by `nextRound()` |
| activeEffects | List\<ActiveEffect\> | All active buffs/debuffs/conditions |

### ActiveEffect
| Field | Type | Notes |
|---|---|---|
| name | String | Display name (e.g. "Aggressiver Angriff") |
| sourceType | SourceType | TALENT, SPELL, CONDITION, EQUIPMENT |
| remainingRounds | int | −1 = permanent; decremented by `nextRound()` |
| negative | boolean | For UI color coding |
| modifiers | List\<ModifierEntry\> | Stat modifiers this effect applies |

### ModifierEntry
| Field | Type | Notes |
|---|---|---|
| targetStat | StatType | Which stat to modify |
| operation | ModifierOperation | ADD, MULTIPLY, OVERRIDE, SET_MIN, SET_MAX |
| value | double | Modifier amount |
| triggerContext | TriggerContext | When modifier is active |

---

## Modifier Engine

`ModifierAggregator.getEffectiveValue(combatant, stat, context)` is the single entry point for all stat lookups. Evaluation order:

1. **Base value** from `GameCharacter` (attribute, armor, defense — derived from discipline/circle/talents)
2. **Equipment modifiers** (armor rating, weapon bonuses)
3. **Talent passive modifiers** (ranked × rank if `rankScaled=true`)
4. **ActiveEffect modifiers** on the `CombatantState` — filtered by `triggerContext == ALWAYS || triggerContext == requestedContext`
5. **Wound penalty** — `wounds × −1` applied to attack/defense/initiative steps

Operations are applied in declaration order: ADD first, then MULTIPLY, then OVERRIDE/SET_MIN/SET_MAX.

### StatType reference
| Stat | Description |
|---|---|
| PHYSICAL_DEFENSE | KV — Körperliche Verteidigung |
| SPELL_DEFENSE | MV — Mystische Verteidigung |
| SOCIAL_DEFENSE | SV — Soziale Verteidigung |
| PHYSICAL_ARMOR | Physische Rüstung |
| MYSTIC_ARMOR | Mystische Rüstung |
| ATTACK_STEP | Angriffsstufe |
| DAMAGE_STEP | Schadensstufe |
| INITIATIVE_STEP | Initiativstufe |
| WOUND_THRESHOLD | Wundschwelle |
| UNCONSCIOUSNESS_RATING | Bewusstlosigkeitsschwelle |
| DEATH_RATING | Todesschwelle |
| KARMA_STEP | Karmawürfel-Stufe (always 4 = W6) |

### TriggerContext reference
| Context | When active |
|---|---|
| ALWAYS | Every calculation |
| ON_MELEE_ATTACK | Attacker rolls melee |
| ON_RANGED_ATTACK | Attacker rolls ranged |
| ON_MELEE_DEFENSE | Defender against melee |
| ON_RANGED_DEFENSE | Defender against ranged |
| ON_SPELL_DEFENSE | Defender against spell |
| ON_DAMAGE_DEALT | Damage roll |
| ON_DAMAGE_RECEIVED | Armor lookup |
| ON_INITIATIVE | Initiative roll |

---

## ED4 Dice & Step System

`StepRollService.roll(step)` converts a step number to dice, rolls them with explosion, and returns a `RollResult`.

**Explosion rule:** When any die rolls its maximum value, roll it again and add. Repeat until no die explodes.

### Step table (excerpt)
| Step | Dice |
|---|---|
| 1 | W4−2 |
| 2 | W4−1 |
| 3 | W4 |
| 4 | W6 |
| 5 | W8 |
| 6 | W10 |
| 7 | W12 |
| 8 | 2W6 |
| 9 | W8+W6 |
| 10 | 2W8 |
| 11 | W10+W8 |
| 12 | 2W10 |
| 13 | W12+W10 |
| 14 | 2W12 |
| ... | ... |

> ⚠️ **Karma die is always W6 (Step 4).** Use `diceService.roll(4)`. Never `roll(6)` — that is Step 6 = W10.

---

## Combat Flow

### Round structure

```
SETUP
  └─ "Initiative würfeln" button
       └─ session.status = ACTIVE
          session.phase = DECLARATION
          session.round = 1

DECLARATION phase (repeats each round)
  └─ Each combatant chooses:
       • Stance: NONE | AGGRESSIVE | DEFENSIVE
       • Action type: WEAPON | SPELL
     (freely changeable until confirmed)
  └─ When ALL non-defeated combatants have declared:
       • Stance ActiveEffects applied (see below)
       • Initiative rolled for all → sorted
       • session.phase = ACTION

ACTION phase
  └─ Combatants act in initiative order
       • Attacks (melee/ranged)
       • Spells (or thread weaving)
       • Free actions (unlimited, no hasActedThisRound)
       • Stand up / Aufspringen
  └─ "Nächste Runde" button
       • round++
       • hasActedThisRound = false for all
       • Stance effects ("Aggressiver Angriff", "Defensive Haltung") removed
       • All other ActiveEffect durations decremented; expired effects removed
       • session.phase = DECLARATION
```

### Stance effects

| Stance | ATTACK_STEP | PHYSICAL/SPELL/SOCIAL_DEFENSE | Additional |
|---|---|---|---|
| AGGRESSIVE | +3 | −3 each | 1 damage to self immediately |
| DEFENSIVE | −3 | +3 each | — |

Effects are stored as `ActiveEffect` (sourceType=CONDITION, remainingRounds=1, name="Aggressiver Angriff" / "Defensive Haltung") and flow through `ModifierAggregator` automatically.

### Attack resolution (`performAttack`)

```
1. Phase check: must be ACTION phase
2. attackStep = getEffectiveValue(attacker, ATTACK_STEP, ON_MELEE/RANGED_ATTACK)
   + optional talent rank bonus + bonusSteps
3. Optional karma: roll(4) [W6], subtract 1 karma
4. attackRoll = roll(attackStep)
5. defenseValue = getEffectiveValue(defender, PHYSICAL_DEFENSE, context)
   + pendingDefenseBonus (consumed immediately)
6. hit = attackTotal > defenseValue
7. If hit:
   extraSuccesses = (total − defense) / 5
   damageStep = getEffectiveValue(attacker, DAMAGE_STEP, ON_DAMAGE_DEALT)
               + weaponBonus + extraSuccesses × 2
   damageRoll = roll(damageStep)
   armor = getEffectiveValue(defender, PHYSICAL_ARMOR, ON_DAMAGE_RECEIVED)
   netDamage = max(0, damage − armor)
8. If defender has Ausweichen talent:
   → hold netDamage as pendingDodgeDamage (defender must resolve dodge)
   Else:
   → applyDamageToDefender (damage track + wound check + knockdown check)
9. attacker.hasActedThisRound = true
```

### Knockdown check (`applyDamageToDefender`)

After taking a wound: defender rolls `TOU-Step vs netDamage`. On failure: `knockedDown = true`.
- **Standhaftigkeit** (passive): If the combatant has this talent, the knockdown roll uses `STR-Step + rank` instead of plain `STR-Step`.
- **Aufstehen**: main action, no roll needed, clears knockedDown.
- **Aufspringen**: `DEX-Step vs 6`, costs 2 damage; success = stand + still act (hasActedThisRound stays false).
- Knockdown immediately removes any **Akrobatische Verteidigung** effect from the defender.

### Health Thresholds (Schwellenwerte)

Computed by `CharacterService.recalculateDerived()` using the official ED4 FASA table (values without discipline circle bonuses):

| ZÄ | ZÄ-Stufe | Wundenschwelle | Bewusstlosigkeit | Todesschwelle |
|:-:|:-:|:-:|:-:|:-:|
| 1–3 | 2 | 3–4 | 2–6 | 4–8 |
| 4–6 | 3 | 4–5 | 8–12 | 11–15 |
| 7–9 | 4 | 6–7 | 14–18 | 18–22 |
| 10–12 | 5 | 7–8 | 20–24 | 25–29 |
| 13–15 | 6 | 9–10 | 26–30 | 32–36 |
| 16–18 | 7 | 10–11 | 32–36 | 39–43 |
| 19–21 | 8 | 12–13 | 38–42 | 46–50 |
| 22–24 | 9 | 13–14 | 44–48 | 53–57 |
| 25 | 10 | 15 | 50 | 60 |

**Formulas:**
```
woundThreshold      = (ZÄ + 1) / 2 + 2          // integer division
unconsciousness     = ZÄ × 2 + bwBonus × (circle − 1)
deathRating         = ZÄ × 2 + attributeToStep(ZÄ) + tdBonus × (circle − 1)
```
`attributeToStep`: every 3 attribute points = +1 step (1–3→2, 4–6→3, 7–9→4, 10–12→5, …)

Per-discipline circle bonuses:
| Discipline | BW-Bonus/circle | TD-Bonus/circle |
|---|:-:|:-:|
| Krieger, Schwertmeister | 7 | 8 |
| Kundschafter, Dieb, Troubadour | 5 | 6 |
| Elementarist, Illusionist, Magier | 3 | 4 |
| No discipline (fallback) | 5 | 6 |

### Configurable Bonuses (Character Sheet)

Every character carries free-text bonus/malus fields that the player adjusts with `+`/`−` steppers on the **Attribute** tab. They are stored directly on `GameCharacter`, applied inside `ModifierAggregator` (so they flow into both the character sheet's derived stats **and** live combat), and edited via `PATCH /api/characters/{id}/field`.

| Field | Section | Applies to | Notes |
|---|---|---|---|
| `physicalDefenseBonus` | Verteidigungs-Boni | KV (Physical Defense) | |
| `spellDefenseBonus` | Verteidigungs-Boni | MV (Spell Defense) | |
| `socialDefenseBonus` | Verteidigungs-Boni | SV (Social Defense) | |
| `healthBonus` | Weitere Boni | **Both** thresholds — Bewusstlosigkeit (BW) **and** Todesschwelle (TD) | mirrors how Holzhaut raises both |
| `initiativeBonus` | Weitere Boni | Initiativestufe (`INITIATIVE_STEP`) | also affects combat initiative rolls |
| `recoveryBonus` | Weitere Boni | Erholungsstufe (`RECOVERY_STEP`) | clamped to a minimum of 0 |

All values may be negative (malus). The character-sheet steppers re-fetch the derived stats after each change so the affected values update immediately.

### Dodge resolution

1. Defender attempts: `DEX-Step + Ausweichen-Rank vs attackTotal`
2. Costs 1 damage to attempt
3. Success: no damage applied
4. Failure: netDamage applied with fresh wound/knockdown check

---

## Main-Action Combat Talents

These talents consume `hasActedThisRound = true` and cost **1 Überanstrengung** (damage) to the user.

**Successes formula** (used by all main-action talents):
```
successes = 1 + floor((total − TN) / 5)   [on success only]
```

### Verspotten
```
Actor: CHA-Step + Verspotten-Rank + bonusSteps − wounds
Target: Social Defense (Soziale VK)
Success: −1/success on all rolls AND social defense of target, for Rank rounds
         Target may auto-counter with Starrsinn (WIL-Step + Starrsinn-Rank vs roll total)
         → on Starrsinn success the Verspotten effect is negated
```

### Ablenken
```
Actor: CHA-Step + Ablenken-Rank + bonusSteps − wounds
Target: Social Defense (Soziale VK)
Success: actor AND target both receive −successes to Physical Defense for 1 round
         ("Toter Winkel" — creates an opening for allies)
```

### Akrobatische Verteidigung
```
Actor: DEX-Step + Akrobatische Verteidigung-Rank + bonusSteps − wounds
TN: highest Physical Defense among all living enemies
Success: +2/success to actor's Physical Defense for 1 round
Restrictions:
  • Cannot combine with Kampfsinn (mutual exclusion — error if Kampfsinn effect active)
  • Effect is removed immediately if actor gets knocked down
```

### Kampfsinn
```
Actor: PER-Step + Kampfsinn-Rank + bonusSteps − wounds
Target: target's Mystic Defense (Mystische VK)
Success: +2/success to actor's Physical Defense AND +2/success to actor's Attack Step for 1 round
Restrictions:
  • Actor must have strictly higher initiative than the target
    (initiativeOrder: 0 = highest; actor.initiativeOrder < target.initiativeOrder)
  • Cannot combine with Akrobatische Verteidigung (mutual exclusion)
```

### Manövrieren
```
Actor: DEX-Step + Manövrieren-Rank + bonusSteps − wounds
Target: target's Physical Defense (Körperliche VK)
Success: successes = 1 + floor((total − TN) / 5)
         +successes×2 to actor's Physical Defense (ON_MELEE_DEFENSE) for 1 round
         +successes×2 to actor's next melee Attack Step (pendingAttackBonus)
Cost:    1 damage to actor; consumes hasActedThisRound
```

### Zweitwaffe
```
Actor: DEX-Step + Zweitwaffe-Rank + bonusSteps − wounds
Target: target's Physical Defense (Körperliche VK)
Success: full damage roll on hit (same resolution as a normal attack)
Restriction:
  • zweitWaffeUsedThisRound = true after use (own once-per-round flag,
    independent of hasActedThisRound — can be used after a main attack
    OR as the sole action for the round)
Cost: 1 damage to actor; sets hasActedThisRound = true
```

---

## Passive / Reaction Talents

These do not consume a main action (unless noted).

### Standhaftigkeit (passive)
When a knockdown check is triggered, `STR-Step + Standhaftigkeit-Rank` is used instead of plain `STR-Step`.

### Starrsinn (auto-counter)
Automatically triggered when the combatant is targeted by **Verspotten**. Rolls `WIL-Step + Starrsinn-Rank vs Verspotten total`. On success, the Verspotten effect is negated.

### Eiserner Wille (free action)
```
Actor: WIL-Step + Eiserner Wille-Rank − wounds
TN: spell roll total (entered manually by the player)
Success: removes the most recently added negative SPELL-sourced ActiveEffect from the actor
Cost: 1 damage; does NOT consume hasActedThisRound
```

### Ausweichen (reaction, after being hit)
See [Dodge resolution](#dodge-resolution) above.

### Riposte (reaction, after being hit by a melee attack)
```
Trigger: a melee attack hits the combatant AND they have the Riposte talent
         AND no other Riposte is already pending
Flow:
  1. performAttack() detects the trigger, stores pendingRiposteAttackTotal and
     pendingRiposteAttackerId on the defender; returns hitPendingRiposte=true
     (damage is NOT applied yet)
  2. Frontend shows the Riposte button (visible only when pendingRiposteAttackTotal ≥ 0)
  3. Defender chooses: attempt Riposte (riposteAttempted=true) or accept damage

  If riposteAttempted=true:
    rollStep = DEX-Step + Riposte-Rank + bonusSteps − wounds
    Roll vs pendingRiposteAttackTotal
    Success: damage blocked entirely
    Extra successes (≥ 1): counter-attack with riposteTotal as attack total
      vs attacker's Physical Defense; damage uses (extraSuccesses − 1) bonus steps

  If riposteAttempted=false: incoming damage applied as normal

Cost: 2 damage to defender regardless of outcome
Does not consume hasActedThisRound
```

### Tigersprung (free action, once per round)
```
No roll.
Actor's initiative += Tigersprung-Rank
Restriction: tigersprungUsedThisRound = true; resets on nextRound()
Cost: 1 damage to actor
Does not consume hasActedThisRound
```

---

## Spell System

### Thread Weaving (Fadenweben)
For spells with `threads > 0`:
```
rollStep = PER-Step + weavingTalentRank − wounds
targetNumber = spell.weavingDifficulty
On success: threadsWoven++
Once threadsWoven >= threadsRequired → spell is ready to cast
Each weave costs 1 action (hasActedThisRound = true)
```

Discipline → Weaving talent mapping:
| Discipline | Talent |
|---|---|
| Elementarist | Elementarismus |
| Illusionist | Illusionismus |
| Magier | Magie |
| Geisterbeschwörer | Geisterbeschwörung |

### Spell Casting (Spruchzauberei)
```
castStep = PER-Step + Spruchzauberei-Rank − wounds
Optional karma: roll(4) [W6]
castRoll = roll(castStep)
defenseValue = spell.castingDifficulty > 0
             ? spell.castingDifficulty
             : getEffectiveValue(target, SPELL_DEFENSE, ON_SPELL_DEFENSE)
success = total > defenseValue (or defenseValue == 0 → auto-success)
extraSuccesses = (total − defense) / 5
```

### Effect types

| Type | Resolution |
|---|---|
| DAMAGE | `WIL-Step + spell.effectStep + extraSuccesses×2` − mystic (or physical) armor |
| BUFF | Adds `ActiveEffect` with `ModifierEntry` on target (or self if no target) |
| DEBUFF | Adds `ActiveEffect` (negative) on target |
| HEAL | Rolls `spell.effectStep`, reduces currentDamage |

---

## Free Actions

Free actions do **not** consume `hasActedThisRound` and can be used an unlimited number of times per round.

### Resolution
```
rollStep = attributeToStep(talent.attribute) + talentRank + bonusSteps − wounds
Optional karma: roll(4) [W6]
targetDefense = getEffectiveValue(target, talent.freeActionTestStat, ALWAYS)
success = total > targetDefense
extraSuccesses = (total − defense) / 5
effectValue = extraSuccesses × talent.freeActionValuePerSuccess
```

On success: an `ActiveEffect` is added to the target (FreeActionTarget.TARGET) or self (FreeActionTarget.SELF) with a `ModifierEntry` of `{ targetStat, ADD, effectValue, triggerContext }` and `remainingRounds = freeActionDuration`.

If `freeActionDamageCost > 0`, the actor takes that damage immediately regardless of success.

### Example: Magische Markierung
- Roll: PER-Step + rank vs target's SPELL_DEFENSE
- Effect: +2 × extraSuccesses added to ATTACK_STEP (ON_RANGED_ATTACK) on target for 1 round
- Cost: 1 damage to self

---

## API Reference

### Characters
```
GET    /api/characters                      List all characters
POST   /api/characters                      Create character
GET    /api/characters/{id}                 Get by ID
PUT    /api/characters/{id}                 Update character
DELETE /api/characters/{id}                 Delete character
PATCH  /api/characters/{id}/field           { field, delta?, absoluteValue? }
PATCH  /api/characters/{id}/notes           { notes }
GET    /api/characters/{id}/derived         Derived stats (KV, MV, UR, etc.)
POST   /api/characters/{id}/recalculate     Recalculate derived stats
POST   /api/characters/{id}/talents         ?talentDefinitionId=&rank=
DELETE /api/characters/{id}/talents/{tid}
POST   /api/characters/{id}/skills          ?skillDefinitionId=&rank=
DELETE /api/characters/{id}/skills/{sid}
POST   /api/characters/{id}/equipment
DELETE /api/characters/{id}/equipment/{eid}
POST   /api/characters/{id}/spells          ?spellDefinitionId=
DELETE /api/characters/{id}/spells/{sid}
```

### Reference Data
```
GET  /api/reference/disciplines
GET  /api/reference/talents
GET  /api/reference/skills
GET  /api/reference/spells              ?discipline= (optional filter)
GET  /api/reference/spells/{id}
```

### Dice
```
POST /api/dice/roll      { step }                          → RollResult
POST /api/dice/probe     ProbeRequest                      → ProbeResult
     { characterId, talentId|skillId, targetNumber, bonusSteps, spendKarma }
```

### Combat Sessions
```
GET    /api/combat/sessions
POST   /api/combat/sessions                { name }
GET    /api/combat/sessions/{id}
DELETE /api/combat/sessions/{id}

POST   /api/combat/sessions/{id}/combatants          ?characterId=&isNpc=
DELETE /api/combat/sessions/{id}/combatants/{cId}
```

### Combat — Round Management
```
POST /api/combat/sessions/{id}/initiative
     → status=ACTIVE, round=1, phase=DECLARATION

POST /api/combat/sessions/{id}/next-round
     → round++, clear stances, phase=DECLARATION

POST /api/combat/sessions/{id}/end
     → status=FINISHED
```

### Combat — Declaration Phase
```
POST /api/combat/sessions/{id}/combatants/{cId}/declare
     ?stance=NONE|AGGRESSIVE|DEFENSIVE
     ?actionType=WEAPON|SPELL
     → saves declaration; if all declared → apply stances + roll initiative + phase=ACTION

POST /api/combat/sessions/{id}/combatants/{cId}/undeclare
     → resets hasDeclared so combatant can re-declare
```

### Combat — Actions (ACTION phase only)
```
POST /api/combat/sessions/{id}/attack                   AttackActionRequest
POST /api/combat/sessions/{id}/dodge                    DodgeRequest
POST /api/combat/sessions/{id}/free-action              FreeActionRequest

POST /api/combat/sessions/{id}/taunt                    TauntRequest
POST /api/combat/sessions/{id}/distract                 DistractRequest
POST /api/combat/sessions/{id}/combat-sense             CombatSenseRequest

POST /api/combat/sessions/{id}/combatants/{cId}/acrobatic-defense
     ?bonusSteps=&spendKarma=
POST /api/combat/sessions/{id}/combatants/{cId}/iron-will
     ?attackTotal=&spendKarma=

POST /api/combat/sessions/{id}/combatants/{cId}/stand-up
POST /api/combat/sessions/{id}/combatants/{cId}/aufspringen   ?spendKarma=

POST /api/combat/sessions/{id}/weave-thread             ThreadweaveRequest
POST /api/combat/sessions/{id}/cast-spell               SpellCastRequest
POST /api/combat/sessions/{id}/combatants/{cId}/cancel-spell

POST /api/combat/sessions/{id}/combatants/{cId}/combat-option   ?option=USE_ACTION

POST /api/combat/sessions/{id}/riposte                          RiposteRequest
     { defenderCombatantId, bonusSteps, spendKarma, riposteAttempted }

POST /api/combat/sessions/{id}/manoeuver                        ManoeuverRequest
     { actorCombatantId, targetCombatantId, bonusSteps, spendKarma }

POST /api/combat/sessions/{id}/combatants/{cId}/tigersprung
     → no body; adds initiative, costs 1 damage

POST /api/combat/sessions/{id}/zweitwaffe                       ZweitwaffeRequest
     { actorCombatantId, defenderCombatantId, weaponId?, bonusSteps, spendKarma }
```

### Combat — State Management
```
PATCH  /api/combat/sessions/{id}/combatants/{cId}/value
       ?field=damage|wounds|karma|initiative|defeated&delta=

POST   /api/combat/sessions/{id}/combatants/{cId}/effects     ActiveEffect (body)
DELETE /api/combat/sessions/{id}/combatants/{cId}/effects/{effectId}

GET    /api/combat/sessions/{id}/log
```

---

## WebSocket

- **Endpoint**: `/ws` (SockJS transport)
- **Subscribe**: `/topic/combat/{sessionId}`
- **Payload**: Full `CombatSession` JSON on every state change (attack, damage, round change, effect added, etc.)
- **Frontend**: `WebSocketService` manages the SockJS/STOMP connection; `CombatTrackerComponent` subscribes and replaces its local session reference on every message.

---

## Frontend Structure

```
src/app/
├── components/
│   ├── character-list/        List + create characters
│   ├── character-sheet/       Full character editor (attributes, talents, equipment, spells)
│   ├── combat-list/           List + create combat sessions
│   ├── combat-tracker/        Main combat UI (declaration, attack, spells, effects, log)
│   └── dice-roller/           Standalone dice roller with talent probe support
├── models/
│   ├── character.model.ts     Character, DisciplineDefinition, TalentDefinition, SpellDefinition
│   ├── combat.model.ts        CombatSession, CombatantState, all request/result types
│   └── dice.model.ts          RollResult, DieRollDetail
└── services/
    ├── character.service.ts   Character CRUD HTTP calls
    ├── combat.service.ts      All combat HTTP calls + declare/undeclare
    ├── dice.service.ts        roll() and probe() calls
    ├── websocket.service.ts   SockJS/STOMP subscription
    └── reference-data.service.ts  Disciplines, talents, skills, spells
```

### Combat Tracker UI phases

**DECLARATION phase** — each combatant card shows:
- Toggle buttons: Neutral / ⚔ Aggressiv / 🛡 Defensiv
- Toggle buttons: 🗡 Waffe / ✨ Zauber (Zauber disabled for non-magic characters)
- "Ansage bestätigen" button (→ POST `/declare`)
- "Ändern" link after confirmation (→ POST `/undeclare`)
- Phase progress badge in header: "📢 Ansagephase (2/4)"
- Attack / spell / free-action buttons are **hidden** during DECLARATION

**ACTION phase** — each combatant card shows:
- ⚔ Aggressiv / 🛡 Defensiv stance badges (read-only, from declaration)
- Attack button (opens dialog) — disabled until it's this combatant's turn
- **Verspotten** button — CHA-based social attack (main action)
- **Akrobatische Verteidigung** button — DEX-based defensive boost (main action)
- **Kampfsinn** button — PER-based offense+defense boost (main action)
- **Ablenken** button — CHA-based mutual defense penalty (main action)
- **Eiserner Wille** button — WIL-based free action to negate spell effect (no turn restriction)
- **Manövrieren** button — DEX-based defensive setup (main action, if talent present)
- **Zweitwaffe** button — additional weapon attack (own once-per-round flag, if talent present)
- **Tigersprung** button — initiative boost, no roll (once per round, if talent present)
- **Riposte** button — reactive melee parry; visible **only** when `pendingRiposteAttackTotal ≥ 0` (i.e. an unresolved melee hit is waiting)
- Threadweave / cast spell buttons (magic characters)
- Free action button (if talent with `freeAction=true` exists)
- Stand up / Aufspringen (if knocked down)
- Phase progress badge: "⚔ Aktionsphase"

---

## Reference Data

Seeded automatically (idempotent — re-running is safe) by `DataInitializer`.

### Disciplines (12)
Krieger, Pfadsucher, Dieb, Schwertkämpfer, Bogenschütze, Waffenmeister, Troubadour, Elementarist, Illusionist, Magier, Geisterbeschwörer, Nekromant

### Key Talents
| Talent | Attribute | Type | Notes |
|---|---|---|---|
| Nahkampfwaffen | DEX | attackTalent | Melee weapon attacks |
| Projektilwaffen | DEX | attackTalent | Ranged weapon attacks |
| Wurfwaffen | DEX | attackTalent | Thrown weapon attacks |
| Waffenloser Kampf | DEX | attackTalent | Unarmed attacks |
| Spruchzauberei | PER | attackTalent | Spell casting |
| Elementarismus | PER | weaving | Thread weaving for Elementarist |
| Illusionismus | PER | weaving | Thread weaving for Illusionist |
| Magie | PER | weaving | Thread weaving for Magier |
| Geisterbeschwörung | PER | weaving | Thread weaving for Geisterbeschwörer |
| Ausweichen | DEX | reaction | Enables dodge after being hit (costs 1 damage) |
| Standhaftigkeit | STR | passive | Knockdown check uses STR+rank instead of STR |
| Starrsinn | WIL | passive | Auto-counter vs Verspotten; WIL+rank vs taunt roll |
| Eiserner Wille | WIL | free action | WIL+rank vs spell total; negates last negative SPELL effect; costs 1 damage |
| Verspotten | CHA | main action | CHA+rank vs SV; −1/success to all rolls+SV of target for Rank rounds |
| Ablenken | CHA | main action | CHA+rank vs SV; −successes KV on actor AND target for 1 round |
| Akrobatische Verteidigung | DEX | main action | DEX+rank vs highest enemy KV; +2/success KV for 1 round |
| Kampfsinn | PER | main action | PER+rank vs target MV; +2/success KV+Angriff for 1 round; requires higher initiative |
| Magische Markierung | PER | free action | freeAction; +2/Übererfolg on ranged ATTACK_STEP vs target; costs 1 damage |
| Manövrieren | DEX | main action | DEX+rank vs target KV; +successes×2 KV+Angriff for 1 round; costs 1 damage |
| Zweitwaffe | DEX | additional attack | DEX+rank vs target KV; full damage on hit; own once-per-round flag; costs 1 damage |
| Riposte | DEX | reaction | Intercepts incoming melee hit; DEX+rank vs attack total; blocks damage; extra successes → counter-attack; costs 2 damage |
| Tigersprung | DEX | free action | No roll; initiative += rank; once per round; costs 1 damage |

### Spells (~105 total)

**Illusionist (Circles 1–8):** Phantomwaffe, Nebelwand, Illusorische Wand, Verwirren, Phantomschmerz, Unsichtbarkeit, Phantomtier, Blendlicht, Geheimnisvolle Gestalt, Phantomflamme, Phantomblitzschlag, Geisterschwert, Verwirrende Waffe, Schattenmantel, Illusorischer Wald, Traumgestalt, Ätherische Rüstung, Astrales Auge, Phantomdrachen, Illusorischer Tod, Psychische Lanze, Gedankenleere, ...

**Geisterbeschwörer (Circles 1–8):** Geisterdolch, Geisterhülle, Geisterrüstung, Astrale Wahrnehmung, Astraler Schild, Geisterpfeil, Geisterwächter, Lebensraub, Geisterzunge, Astrales Tor, Geisterheer, Bannfluch, Astraler Körper, Seelenraub, Geisterform, Todeshauch, Geisterbindung, Astraler Sturm, ...

---

## Versioning

The project follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`). The version is kept in sync between `backend/pom.xml` (`<version>`) and `frontend/package.json` (`version`).

### Changelog

#### 1.2.0 (in development)
- **New talent: Verängstigen** — WIL + rank vs. the target's mystic defense (standard action, 0 strain). On success the target is *verängstigt*: **−2 on all action tests per success** for **rank** rounds. Each round the target may attempt a willpower test against the adept's Verängstigen step (WIL step + rank) via a "Furcht abschütteln" button — success ends the effect early (once per round). Discipline talent of the Geisterbeschwörer (1st circle), talent option for Illusionists (circles 5–8). Flyway `V35`.
- **Fix — Schwimmen talent no longer deleted on restart** — the startup cleanup of unimplemented talents still listed *Schwimmen* and stripped it (including character assignments) on every boot before re-seeding it; it is now kept.
- **Arzt rework — two treatment modes** — the Arzt skill now distinguishes:
  - **Verletzungen behandeln** (treat lost HP): only **once per recovery test**; on success grants **+rank** on the next recovery roll (`arztInjuryTreated`, reset by the recovery test).
  - **Wunde versorgen** (dress a wound): on success suppresses the **−1 wound modifier of one wound** on recovery tests; repeatable until **all wounds are dressed** (`arztWoundsTreated` counter, persists until wounds heal).
  - Both modes roll PER-step + rank vs. fixed DN 5 and consume **1× Verbandszeug each — even on failure**. Flyway `V34` (replaces the old all-or-nothing `arztWoundPenaltyNegated` flag).

#### 1.1.0
- **Schwanzangriff (T'skrang tail attack)** — racial ability for T'skrang: an extra unarmed tail attack (1×/round, doesn't consume the main action), resolved via Waffenloser Kampf vs. physical defense with STR-based damage. A melee weapon can be tail-mounted via the new `tailWeapon` flag (`🦎 Schwanzwaffe`). Using it imposes **−2 on all rolls that round**. A mix of Krallenhand and Zweitwaffe. Flyway `V33`.
- **Weapons assignable to an attack talent** — a weapon can be tied to an attack talent/skill (Nahkampfwaffen, Projektilwaffen, Wurfwaffen, Waffenloser Kampf) via `Equipment.attackTalentName`. In combat the weapon dropdown then only offers weapons matching the selected talent/skill; weapons left unassigned stay available for every attack (backward compatible). Flyway `V32`.
- **Richer combat log** — the log is now ordered newest-first (chronologically descending) and attack entries show the exact dice breakdown for the **attack** and **damage** rolls (individual dice, karma die, total), the **strain** cost, and all applied **modifiers** (attack/damage bonus notes). Stored per entry in `CombatLog.rollDetailsJson`.
- **Combat end broadcast** — ending a combat now pushes a synchronized "Kampf beendet" modal to **all** connected clients (plus a persistent 🏁 badge in the tracker), so spectators are notified rather than left on a frozen screen.
- **Attack dialog limited to weapon attack talents** — the attack source dropdown now only offers the weapon attack talents (**Nahkampfwaffen, Projektilwaffen, Wurfwaffen, Waffenloser Kampf**) plus the weapon skills; unrelated talents and Spruchzauberei (spell-cast flow) are no longer listed.
- **GM conditions (manually activated)** — two new GM-applied combat conditions (target + duration chosen in the GM-effect dialog):
  - **Toter Winkel** (blind-spot attack): −2 KV/MV on the target, and **no active defense talents** (Ausweichen/Riposte) may be used against it (suppressed in `performAttack`).
  - **Bedrängt** (harried): −2 to attack rolls, KV and MV. Re-applying stacks cumulatively by −1 each (*Überwältigt*). Combines additively with Toter Winkel.
- **Schwimmen (Talent + Fertigkeit)** — new STR-based *Schwimmen* talent **and** skill, seeded idempotently. Plus the magic item **Schwimmkristall** (GEAR quick-add): **+3 auf Schwimmen** and "Erlaubt Unterwasseratmung von Rang Minuten".
- **Combat spell selection limited to matrices** — in combat, the thread-weave and cast dialogs only offer spells assigned to a **Zaubermatritze** or **Erweiterte Matrize**.
- **Karma auf Erholungsproben** — disciplines may spend 1 Karma for a **+W6 (Step 4)** die on a recovery test. Eligible: **Elementarist, Krieger, Luftpirat, Tiermeister, Waffenschmied** from **3rd circle**, **Kundschafter** from **5th circle**. An optional Karma checkbox appears on the recovery (Erholung) page when discipline and circle qualify; `POST /api/characters/{id}/recovery-test?spendKarma=true`.
- **Karma auf Initiative** — disciplines **Dieb, Kundschafter, Luftsegler, Schütze** may, from **3rd circle**, spend 1 Karma for a **+W6 (Step 4)** die on their initiative roll. A toggle button appears on the combatant card during the **declaration phase** when discipline and circle qualify; the Karma is deducted when initiative is actually rolled. Backed by Flyway migration `V31`.
- **Configurable stat bonuses** — in addition to the existing defense bonuses (KV / MV / SV), the **Attribute** tab now exposes a *Weitere Boni* section with bonus/malus steppers for:
  - **Lebenspunkte** (`healthBonus`) — added to both the unconsciousness **and** death thresholds.
  - **Initiative** (`initiativeBonus`) — added to the initiative step (also applies in combat).
  - **Erholungsstufe** (`recoveryBonus`) — added to the recovery step (clamped at 0).
  - See [Configurable Bonuses](#configurable-bonuses-character-sheet). Backed by Flyway migration `V30`.
- **Dice breakdown on recovery & initiative** — recovery tests now show the individual rolled dice (incl. the karma die and the combined total); the initiative karma die is rendered as its own die (like every other roll) instead of a "+ Karma" bonus chip.
- **Fix — dodge/riposte result sync** — when a defender uses Ausweichen or Riposte, the resolution result is now pushed to **all** spectators via WebSocket (previously only the acting client saw it).
- **Fix — Lufttanz bonus attack** — the extra melee attack is now offered whenever the initiative lead is ≥ 10, **regardless of whether the triggering attack hits** (previously gated on a hit).

#### 1.0.0
- Initial release: characters, equipment, talents/skills/spells, full ED4 turn-based combat with live WebSocket updates.
