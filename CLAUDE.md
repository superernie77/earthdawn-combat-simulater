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
- **Step system**: Each step maps to specific dice (Step 1=d4-2, Step 3=d4, Step 5=d8, Step 8=2d6, etc.)
- **Exploding dice**: Roll again and add on max value
- **Attributes = Steps**: In ED4 FASA, attribute value directly equals step number (1:1)
- **Probe**: Attribut-Step + Talent-Rang vs Schwierigkeitswert. Extra Erfolge: (Ergebnis - SW) / 5
- **Combat**: Initiative → Angriff → Schaden → Wunde → nächste Runde
- **Karma**: Spend 1 Karma → roll Karma-Step and add to result

## Core Architecture: Modifier Engine
Every bonus/penalty goes through `ModifierAggregator`. Modifiers have:
- `targetStat`: which stat is modified (PHYSICAL_DEFENSE, ATTACK_STEP, etc.)
- `operation`: ADD | MULTIPLY | OVERRIDE | SET_MIN | SET_MAX
- `triggerContext`: ALWAYS | ON_MELEE_ATTACK | ON_DEFENSE | etc.
- `value`: numeric value
Talents, spells, conditions, equipment = all stored as `ActiveEffect` with `ModifierEntry` list on `CombatantState`.

## Package Structure
```
com.earthdawn
├── config/       WebSocketConfig, CorsConfig, JacksonConfig
├── controller/   CharacterController, CombatController, DiceController, ReferenceDataController
├── dto/          RollResult, DieRollDetail, ProbeRequest/Result, AttackActionRequest, CombatActionResult, FieldUpdateRequest, DerivedStats
├── model/        Character, DisciplineDefinition, TalentDefinition, SkillDefinition,
│                 CharacterTalent, CharacterSkill, ModifierEntry, ActiveEffect,
│                 CombatSession, CombatantState, CombatLog
│   └── enums/    StatType, AttributeType, ModifierOperation, TriggerContext, SourceType, ActionType, CombatStatus
├── repository/   CharacterRepository, CombatSessionRepository, DisciplineRepository,
│                 TalentDefinitionRepository, SkillDefinitionRepository
└── service/      StepRollService, ModifierAggregator, CharacterService, CombatService,
                  ProbeService, DataInitializer
```

## Frontend Routes
```
/              → redirect to /characters
/characters    → CharacterListComponent
/characters/:id → CharacterSheetComponent
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

POST   /api/dice/roll        { step }
POST   /api/dice/probe       ProbeRequest

GET    /api/combat/sessions
POST   /api/combat/sessions  { name }
GET    /api/combat/sessions/{id}
POST   /api/combat/sessions/{id}/combatants       ?characterId=
POST   /api/combat/sessions/{id}/initiative
POST   /api/combat/sessions/{id}/attack           AttackActionRequest
POST   /api/combat/sessions/{id}/next-round
PATCH  /api/combat/sessions/{id}/combatants/{cId}/value  ?field=&delta=
POST   /api/combat/sessions/{id}/combatants/{cId}/effects
DELETE /api/combat/sessions/{id}/combatants/{cId}/effects/{effectId}
GET    /api/combat/sessions/{id}/log
```

## WebSocket
- Endpoint: `/ws` (SockJS)
- Topic: `/topic/combat/{sessionId}` — broadcasts full `CombatSession` on every state change
- Frontend subscribes in `CombatTrackerComponent` via `WebSocketService`

## Reference Data
Seeded automatically on first start by `DataInitializer`:
- 8 Disziplinen: Krieger, Pfadsucher, Dieb, Elementarist, Nekromant, Illusionist, Schwertkämpfer, Troubadour
- 21 Talente: Kampfwaffen, Fernwaffen, Ausweichen, Lufttanz, Schleichen, Zauberspruch, etc.
- 12 Fertigkeiten: Reiten, Kartenkunde, Geschichte, Alchimie, Schmieden, etc.
