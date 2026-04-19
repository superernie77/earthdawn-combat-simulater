# Earthdawn Combat Simulator — Project Overview for Claude

## What This Is
A local dev tool for simulating Earthdawn 4th Edition (FASA) pen-and-paper RPG combat.
Characters with attributes, disciplines, talents and skills are managed and used in
turn-based combat sessions with live updates via WebSocket.

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.5, Maven (`./mvnw`), Spring Data JPA, PostgreSQL, Spring WebSocket (STOMP)
- **Frontend**: Angular 20 standalone components, Angular Material dark theme
- **Database**: PostgreSQL via Docker (`docker-compose up -d`)

## Starting the App
```bash
# PostgreSQL
docker-compose up -d

# Backend (from backend/)
./mvnw spring-boot:run

# Frontend (from frontend/)
npm install
npx ng serve
```
Backend: `http://localhost:8080` | Frontend: `http://localhost:4200`

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
| **Kampfsinn** | PER | MV des Ziels | **Freie Aktion.** +2 KV + +2 Angriff/Erfolg für 1 Runde. Nur vs. Ziele mit niedrigerer Initiative. Nicht mit Akrobatischer Verteidigung. |

**Successes formula** for all main-action talents: `1 + floor((total − TN) / 5)` on success.

### Passive / Reaction Talents
- **Standhaftigkeit**: Passiv. Bei Niederschlagsprobe: STR-Step + Talentrang statt reiner STR-Step.
- **Starrsinn**: Auto-Gegenprobe gegen Verspotten. WIL-Step + Rang vs. Verspotten-Ergebnis. Bei Erfolg: Effekt negiert.
- **Eiserner Wille**: Freie Aktion. WIL-Step + Rang vs. Zauberwurf des Angreifers (manuell eingegeben). Bei Erfolg: jüngster negativer Zaubereffekt entfernt.
- **Ausweichen**: Nach Treffer. DEX-Step + Rang vs. Angriffswurf. Kostet 1 Schaden.

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
├── controller/   CharacterController, CombatController, DiceController, ReferenceDataController
├── dto/          RollResult, DieRollDetail, ProbeRequest/Result,
│                 AttackActionRequest, CombatActionResult,
│                 DodgeRequest/Result, StandUpResult, KnockdownResult,
│                 FreeActionRequest/Result,
│                 TauntRequest/Result, DistractRequest/Result,
│                 AcrobaticDefenseResult, CombatSenseRequest/Result,
│                 IronWillResult,
│                 ThreadweaveRequest/Result, SpellCastRequest/Result,
│                 DerivedStats, FieldUpdateRequest
├── model/        GameCharacter, DisciplineDefinition, TalentDefinition, SkillDefinition,
│                 CharacterTalent, CharacterSkill, Equipment,
│                 ModifierEntry, ActiveEffect,
│                 SpellDefinition,
│                 CombatSession, CombatantState, CombatLog
│   └── enums/    StatType, AttributeType, ModifierOperation, TriggerContext, SourceType,
│                 ActionType, CombatStatus, CombatPhase,
│                 DeclaredStance, DeclaredActionType,
│                 SpellEffectType, FreeActionTarget
├── repository/   CharacterRepository, CombatSessionRepository, DisciplineRepository,
│                 TalentDefinitionRepository, SkillDefinitionRepository,
│                 SpellDefinitionRepository
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
POST   /api/combat/sessions/{id}/combat-sense        CombatSenseRequest
POST   /api/combat/sessions/{id}/combatants/{cId}/stand-up
POST   /api/combat/sessions/{id}/combatants/{cId}/aufspringen    ?spendKarma=
POST   /api/combat/sessions/{id}/combatants/{cId}/acrobatic-defense  ?bonusSteps=&spendKarma=
POST   /api/combat/sessions/{id}/combatants/{cId}/iron-will      ?attackTotal=&spendKarma=

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
- Reaction / passive: Ausweichen, Standhaftigkeit, Starrsinn, Eiserner Wille
- Social actions: Verspotten (CHA), Ablenken (CHA)
- Defensive actions: Akrobatische Verteidigung (DEX), Kampfsinn (PER)

**Spells (~105 total):** Illusionist (Circles 1–8) + Geisterbeschwörer (Circles 1–8)

## Key Implementation Notes
- **Karma die**: Always Step 4 = W6 for all disciplines. `diceService.roll(4)` everywhere. **Do not use** `roll(6)` (that is Step 6 = W10).
- **Stance effects**: Applied as `ActiveEffect` with `ATTACK_STEP` modifier (not via request flags). Aggressive adds `+3 ATTACK_STEP` + `-3` to all defenses; Defensive adds `-3 ATTACK_STEP` + `+3` to all defenses. Both cleared at round end in `nextRound()`.
- **CombatPhase**: `DECLARATION` (all declare before initiative) → `ACTION` (fight). Phase stored on `CombatSession`. Auto-transitions to `ACTION` when all non-defeated combatants have declared.
- **Successes formula**: `successes = 1 + floor((total − TN) / 5)` when successful. Used for Verspotten, Ablenken, Akrobatische Verteidigung, Kampfsinn.
- **Knockdown removes Akrobatische Verteidigung**: In `performKnockdownCheck()`, when `knocked = true`, the effect is explicitly removed: `defender.getActiveEffects().removeIf(e -> "Akrobatische Verteidigung".equals(e.getName()))`.
- **Kampfsinn initiative check**: `actor.getInitiativeOrder() < target.getInitiativeOrder()` (lower order = higher initiative, since combatants are sorted descending).
- **Eiserner Wille removes SPELL-sourced effects**: On success, removes the most recently added `ActiveEffect` with `sourceType == SourceType.SPELL` and `negative == true` from the actor.
- **SpellService** uses `ModifierAggregator` for `KARMA_STEP` and spell defense values — same engine as physical combat.
- **Free actions**: `TalentDefinition.freeAction=true` triggers `performFreeAction()` in `CombatService`. Does not set `hasActedThisRound`. Eiserner Wille is a separate endpoint (not via the generic free-action path) because it needs a manual `attackTotal` input.
