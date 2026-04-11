# Earthdawn Combat Simulator

A local combat tracker for the **Earthdawn 4th Edition** pen-and-paper RPG. Manages combat sessions, resolves attacks and dodge rolls using the ED4 step/dice system, tracks wounds and effects, and implements special mechanics like knockdown (Niedergeschlagen).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Spring WebFlux |
| Frontend | Angular 20, Angular Material (dark theme), standalone components |
| Database | PostgreSQL (via Docker) |
| Real-time | WebSocket (STOMP) |

## Features

- **Character management** — attributes, discipline, circle, talents, skills, equipment
- **Combat session tracker** — initiative rolling, turn order, round management
- **Attack resolution** — melee, ranged, spell attacks with weapon talents and karma
- **Dodge system** — optional dodge attempt costs 1 damage; pending damage held until resolved
- **Damage & wounds** — armor subtraction, wound threshold, wound penalties on all rolls
- **Knockdown (Niedergeschlagen)** — triggered when damage exceeds wound threshold by 5+; STR test vs excess damage; −3 on all tests and defenses until standing up
  - *Aufstehen* — stand up as a main action
  - *Aufspringen* — DEX test vs 6, costs 2 damage, skips the Aufstehen action
- **Active effects** — modifiers applied per stat with trigger contexts (always, on attack, on defense, etc.)
- **Free actions** — talent-based free combat actions (e.g. Magische Markierung)
- **Aggressive / Defensive stance** — dialog toggles with stat trade-offs
- **Derived stats** — Bewusstlosigkeitsschwelle / Todesschwelle scale with discipline and circle
- **Karma** — pill-toggle on all roll dialogs; always rolls W6 (step 4)

## ED4 Dice Engine

Steps map to dice according to the official ED4 step table. Dice explode on their maximum value (W4 explodes on 4, W6 on 6, etc.). Multi-die steps (e.g. step 14 = W10+W8) sum all dice after explosion.

```
Step  4 = W6
Step  5 = W8
Step  6 = W10
Step  7 = W12
Step  8 = 2×W6
...
```

## Getting Started

### Prerequisites

- Java 21+
- Node.js 20+
- Docker (for PostgreSQL)

### 1. Start the database

```bash
docker-compose up -d
```

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8081`.

### 3. Start the frontend

```bash
cd frontend
npm install
npx ng serve --open
```

Frontend runs on `http://localhost:4200`.

## Project Structure

```
earthdawn-combat/
├── backend/
│   └── src/main/java/com/earthdawn/
│       ├── config/          # CORS, WebSocket, Jackson
│       ├── controller/      # CharacterController, CombatController, DiceController
│       ├── dto/             # Request/response DTOs
│       ├── model/           # JPA entities + enums
│       ├── repository/      # Spring Data repositories
│       └── service/         # CombatService, CharacterService, StepRollService, ...
└── frontend/
    └── src/app/
        ├── components/      # combat-tracker, character-sheet, dice-roller, ...
        ├── models/          # TypeScript interfaces
        └── services/        # HTTP + WebSocket services
```

## API Endpoints

```
# Characters
GET    /api/characters
POST   /api/characters
PUT    /api/characters/{id}
POST   /api/characters/{id}/recalculate

# Combat
POST   /api/combat/sessions
GET    /api/combat/sessions/{id}
POST   /api/combat/sessions/{id}/initiative
POST   /api/combat/sessions/{id}/attack
POST   /api/combat/sessions/{id}/dodge
POST   /api/combat/sessions/{id}/free-action
POST   /api/combat/sessions/{id}/next-round
POST   /api/combat/sessions/{id}/combatants/{cId}/stand-up
POST   /api/combat/sessions/{id}/combatants/{cId}/aufspringen

# Dice
POST   /api/dice/roll          # { step: number }
```

## License

Private project — not licensed for redistribution.
