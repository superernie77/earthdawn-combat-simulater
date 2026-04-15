# Spellcasting Feature — Implementation Plan

## Overview

Add a full spellcasting system to the Earthdawn Combat Simulator. Spells are discipline-specific magical abilities that can deal damage (against **mystical defense/armor**) or apply buff/debuff effects to characters. Two new talents — **Fadenweben** (Threadweaving) and **Spruchzauberei** (Spellcasting) — drive the mechanics. Some spells require threads to be woven before casting; others can be cast directly.

---

## ED4 Spellcasting Rules Summary

1. **Threadweaving** (Fadenweben): A caster weaves magical threads into a spell matrix before casting. Each thread requires a separate Fadenweben test vs. the spell's **Fadenweben-Schwierigkeit** (weaving difficulty). Each thread costs one action (one round per thread). Some spells need 0 threads and can be cast immediately.
2. **Spellcasting** (Spruchzauberei): Once all threads are woven, the caster makes a Spruchzauberei test vs. the target's **Zauberverteidigung** (Spell Defense) — or vs. a fixed difficulty for self/ally buffs. On success, the spell takes effect.
3. **No aggressive casting**: Spells cannot be cast aggressively (no +3 / -3 mechanic).
4. **No dodge against spells**: Targets cannot use Ausweichen (Dodge) to avoid spells.
5. **Damage spells**: Roll the spell's effect step for damage. Reduced by **Mystische Rüstung** (Mystic Armor) instead of Physical Armor. Extra successes on the spellcasting test add +2 damage steps per extra success (same as melee).
6. **Buff/Debuff spells**: Apply an `ActiveEffect` with modifiers to the target (or self). Duration is spell-defined.
7. **Each discipline has its own Fadenweben flavor**: Elementarist → "Elementarismus", Illusionist → "Illusionismus", Magier → "Magie". These are cosmetic labels but each is a separate talent with its own rank.

---

## Data Model Changes

### New Entity: `SpellDefinition`

```java
@Entity
@Table(name = "spell_definitions")
public class SpellDefinition {
    Long id;
    String name;                     // e.g. "Feuerball", "Geisterpfeil"
    String discipline;               // "Elementarist", "Illusionist", "Magier" (or null = generic)
    int circle;                      // minimum circle required to learn
    int threads;                     // number of threads to weave (0 = instant cast)
    int weavingDifficulty;           // target number for Fadenweben test
    int castingDifficulty;           // target number for Spruchzauberei (0 = use target's spell defense)
    
    // --- Effect Type ---
    SpellEffectType effectType;      // DAMAGE, BUFF, DEBUFF, HEAL
    
    // --- Damage spells ---
    int effectStep;                  // damage step (or healing step)
    boolean useMysticArmor;          // true (default) — some rare spells use physical armor
    
    // --- Buff/Debuff spells ---
    StatType modifyStat;             // which stat the spell modifies (nullable)
    ModifierOperation modifyOperation; // ADD, MULTIPLY, etc.
    double modifyValue;              // modifier value
    TriggerContext modifyTrigger;    // when the modifier applies
    int duration;                    // rounds (-1 = permanent until dispelled)
    
    // --- UI ---
    String description;              // flavor text + rules summary (1000 chars)
    String effectDescription;        // short effect text for combat log
}
```

### New Enum: `SpellEffectType`
```java
DAMAGE, BUFF, DEBUFF, HEAL
```

### New Entity: `CharacterSpell`

Links a spell to a character (learned spells):

```java
@Entity
@Table(name = "character_spells")
public class CharacterSpell {
    Long id;
    GameCharacter character;         // FK
    SpellDefinition spellDefinition; // FK, EAGER
}
```

### New Fields on `GameCharacter`

```java
@OneToMany(mappedBy = "character", cascade = ALL, orphanRemoval = true, fetch = EAGER)
List<CharacterSpell> spells = new ArrayList<>();
```

### New Fields on `CombatantState`

```java
// Tracks thread-weaving progress for the currently prepared spell
Long preparingSpellId;          // FK to SpellDefinition (null = not preparing)
int threadsWoven;               // how many threads have been woven so far
int threadsRequired;            // total threads needed (copied from spell on start)
```

### New Entity: `SpellDefinition` Relationship to `DisciplineDefinition`

No direct FK needed. `SpellDefinition.discipline` is a String matching `DisciplineDefinition.name`. Query by discipline name to get available spells. This keeps the schema simple and allows a spell to belong to multiple disciplines if needed in the future (by adding multiple rows or changing to a join table).

---

## New DTOs

### `SpellCastRequest`
```java
Long sessionId;
Long casterCombatantId;
Long targetCombatantId;     // null for self-targeting spells
Long spellId;               // SpellDefinition ID
boolean spendKarma;
```

### `ThreadweaveRequest`
```java
Long sessionId;
Long casterCombatantId;
Long spellId;               // SpellDefinition ID (to start or continue weaving)
boolean spendKarma;
```

### `ThreadweaveResult`
```java
String casterName;
String spellName;
int rollStep;
RollResult roll;
RollResult karmaRoll;
int targetNumber;           // weaving difficulty
boolean success;
int threadsWoven;           // total after this attempt
int threadsRequired;
boolean readyToCast;        // threadsWoven >= threadsRequired
String description;
```

### `SpellCastResult`
```java
String casterName;
String targetName;
String spellName;
SpellEffectType effectType;
int castStep;
RollResult castRoll;
RollResult karmaRoll;
int defenseValue;           // target's spell defense (or fixed difficulty)
boolean success;
int extraSuccesses;

// Damage spells
int damageStep;
RollResult damageRoll;
int armorValue;             // mystic armor (usually)
int netDamage;
boolean woundDealt;
int newWounds;
int totalWounds;
int woundThreshold;
boolean targetDefeated;
KnockdownResult knockdownResult;

// Buff/Debuff spells
String effectApplied;       // description of the applied effect
int effectDuration;

String description;
```

---

## New ActionTypes

Add to `ActionType` enum:
```java
THREADWEAVE,    // Faden weben
SPELL_CAST      // Zauber wirken (rename existing SPELL_ATTACK or keep both)
```

Note: `SPELL_ATTACK` already exists. We can reuse it for damage spells in the combat log. `THREADWEAVE` is new. Add `SPELL_CAST` for non-damage spells (buffs/debuffs/heals).

Update `migrateActionTypeConstraint()` to include the new values.

---

## Backend Service Changes

### New: `SpellService`

Handles spell logic, keeping `CombatService` focused on melee/ranged combat.

#### `ThreadweaveResult weaveThread(ThreadweaveRequest req)`

1. Validate caster is not defeated, has not acted this round.
2. If `preparingSpellId == null` → start new preparation: set `preparingSpellId`, `threadsWoven = 0`, `threadsRequired = spell.threads`.
3. If `preparingSpellId != req.spellId` → error: already preparing a different spell.
4. Get caster's **Fadenweben talent rank** (look up by discipline flavor name — see mapping below).
5. Roll step = attribute step (PER) + Fadenweben rank + bonusSteps.
6. Optional karma.
7. Compare roll vs. `spell.weavingDifficulty`.
8. On success: `threadsWoven++`.
9. On failure: no progress, thread is lost.
10. Set `hasActedThisRound = true`.
11. If `threadsWoven >= threadsRequired` → set `readyToCast = true`.
12. Log, save, broadcast.

**Fadenweben talent mapping** (by discipline):
| Discipline        | Fadenweben Talent Name |
|-------------------|------------------------|
| Elementarist      | Elementarismus         |
| Illusionist       | Illusionismus          |
| Magier            | Magie                  |
| Geisterbeschwörer | Geisterbeschwörung     |
| (generic/all)     | Fadenweben             |

These are added as new TalentDefinitions in the DataInitializer migration.

#### `SpellCastResult castSpell(SpellCastRequest req)`

1. Validate caster is not defeated, has not acted this round.
2. Validate spell threads are complete: `threadsWoven >= threadsRequired` (or `threads == 0`).
3. Get caster's **Spruchzauberei rank** from talents.
4. Cast step = PER step + Spruchzauberei rank.
5. Optional karma.
6. Determine defense:
   - If `spell.castingDifficulty > 0` → use that (self-buffs, area spells).
   - Else → target's Spell Defense via `ModifierAggregator`.
7. Roll cast test vs. defense.
8. On hit:
   - **DAMAGE**: extra successes (+2 step each), roll `spell.effectStep` for damage, subtract mystic armor (or physical if `!useMysticArmor`), apply via `applyDamageToDefender()`. **No dodge check** — skip the Ausweichen path entirely.
   - **BUFF/DEBUFF**: create `ActiveEffect` with `sourceType = SPELL`, apply to target (or self). Duration from spell definition.
   - **HEAL**: reduce target's `currentDamage` by roll result.
9. Clear `preparingSpellId`, `threadsWoven`, `threadsRequired` (spell is spent).
10. Set `hasActedThisRound = true`.
11. Log, save, broadcast.
12. Check for knockdown (damage spells only, same rules as melee).

### Changes to `CombatService`

- Extract `applyDamageToDefender()` to be callable from `SpellService` (or make it a shared utility method, or keep in CombatService and call from SpellService).
- `nextRound()`: reset thread-weaving state? **No** — threads persist across rounds. A caster can weave 1 thread per round over multiple rounds. Only reset on spell cast or explicit cancel.
- Add a `cancelSpellPreparation(sessionId, combatantId)` method to abandon a partially woven spell.

### New: `SpellDefinitionRepository`

```java
public interface SpellDefinitionRepository extends JpaRepository<SpellDefinition, Long> {
    List<SpellDefinition> findByDisciplineOrderByCircleAscNameAsc(String discipline);
    List<SpellDefinition> findAllByOrderByDisciplineAscCircleAscNameAsc();
}
```

### New: `CharacterSpellRepository`

Not strictly needed if using cascade from GameCharacter, but useful for queries:
```java
public interface CharacterSpellRepository extends JpaRepository<CharacterSpell, Long> {}
```

---

## Controller Changes

### `CombatController` — New Endpoints

```
POST /api/combat/sessions/{id}/weave-thread     → ThreadweaveResult
POST /api/combat/sessions/{id}/cast-spell        → SpellCastResult
POST /api/combat/sessions/{id}/combatants/{cId}/cancel-spell → CombatSession
```

### `CharacterController` — New Endpoints

```
GET  /api/spells                                 → List<SpellDefinition> (all spells)
GET  /api/spells?discipline=Elementarist         → List<SpellDefinition> (filtered)
POST /api/characters/{id}/spells                 → GameCharacter (assign spell)
DELETE /api/characters/{id}/spells/{spellId}      → void (remove spell)
```

---

## Frontend Changes

### New TypeScript Models (`combat.model.ts` / `character.model.ts`)

```typescript
interface SpellDefinition {
  id: number;
  name: string;
  discipline: string;
  circle: number;
  threads: number;
  weavingDifficulty: number;
  castingDifficulty: number;
  effectType: 'DAMAGE' | 'BUFF' | 'DEBUFF' | 'HEAL';
  effectStep: number;
  useMysticArmor: boolean;
  modifyStat?: string;
  modifyOperation?: string;
  modifyValue?: number;
  modifyTrigger?: string;
  duration: number;
  description: string;
  effectDescription: string;
}

interface CharacterSpell {
  id: number;
  spellDefinition: SpellDefinition;
}

interface ThreadweaveResult { ... }
interface SpellCastResult { ... }
```

Add to `CombatantState`:
```typescript
preparingSpellId?: number;
threadsWoven: number;
threadsRequired: number;
```

### Character Sheet Component — New "Sprüche" Tab

Between "Ausrüstung" and "Notizen" tabs, add a **Sprüche** (Spells) tab:

- Lists all spells assigned to this character.
- "Zauber hinzufügen" button → opens a dialog showing available spells for the character's discipline (+ circle filter).
- Each spell card shows: name, circle, threads, effect type icon, effect description.
- Remove button per spell.
- Only visible if character has a magic discipline (Elementarist, Illusionist, Magier).

### Combat Tracker Component — Spellcasting UI

#### Combatant Card Changes
- Show spell preparation state: "⟡ Feuerball (2/3 Fäden)" badge when `preparingSpellId != null`.
- New action buttons (visible only for magic-users):
  - **"Faden weben"** → opens threadweave dialog
  - **"Zauber wirken"** → opens spell cast dialog (enabled only when threads complete or spell.threads == 0)

#### Threadweave Dialog
- Dropdown: select a spell from caster's learned spells
- If caster is already weaving a different spell: show warning "Du webst bereits an [SpellName]. Fortfahren?"
- Show: spell name, threads needed, threads woven so far, weaving difficulty
- Karma toggle (checkbox)
- "Faden weben" button → calls `/weave-thread`
- Result modal: shows roll, success/fail, progress bar (threadsWoven / threadsRequired)

#### Spell Cast Dialog
- Shows spells that are ready to cast:
  - Spells with `threads == 0` (always available)
  - The currently prepared spell (if `threadsWoven >= threadsRequired`)
- Target selector: dropdown of all combatants (or "Selbst" for self-buffs)
- Show: spell name, effect type, expected damage step / effect description
- Karma toggle
- "Zauber wirken" button → calls `/cast-spell`

#### Spell Cast Result Modal
- **Damage spells**: shows cast roll, defense, hit/miss, damage roll, mystic armor, net damage, wounds, knockdown (reuse existing damage result layout)
- **Buff/Debuff spells**: shows cast roll, defense, hit/miss, effect applied, duration
- **Heal spells**: shows cast roll, amount healed

---

## Seed Data — Example Spells

### Elementarist Spells (Circle 1-3)

| Name | Circle | Threads | WD | Effect | Step/Value | Duration | Description |
|------|--------|---------|-----|--------|------------|----------|-------------|
| Feuerball | 2 | 2 | 8 | DAMAGE | 10 | - | Schleudert einen Feuerball auf das Ziel |
| Eisnadeln | 1 | 1 | 6 | DAMAGE | 6 | - | Nadeln aus Eis treffen das Ziel |
| Erdbeben | 3 | 3 | 10 | DAMAGE | 12 | - | Die Erde bebt unter dem Ziel |
| Flammenrüstung | 2 | 2 | 8 | BUFF | +3 | 3 rounds | +3 Mystische Rüstung für den Zauberer |
| Windschutz | 1 | 0 | - | BUFF | +2 | 2 rounds | +2 Körperliche Verteidigung |
| Flammenpfeil | 1 | 0 | - | DAMAGE | 4 | - | Ein kleiner Feuerpfeil |

### Illusionist Spells (Circle 1-3)

| Name | Circle | Threads | WD | Effect | Step/Value | Duration | Description |
|------|--------|---------|-----|--------|------------|----------|-------------|
| Geisterpfeil | 1 | 0 | - | DAMAGE | 4 | - | Ein geisterhafter Pfeil trifft das Ziel |
| Trugbild | 2 | 1 | 7 | DEBUFF | -2 | 2 rounds | -2 auf Angriffsstufe des Ziels |
| Nebelwand | 2 | 2 | 8 | BUFF | +3 | 3 rounds | +3 Körperliche Verteidigung für Verbündeten |
| Phantomschmerz | 3 | 2 | 9 | DAMAGE | 8 | - | Illusionärer Schmerz, Schaden gegen MV |

### Magier Spells (Circle 1-3)

| Name | Circle | Threads | WD | Effect | Step/Value | Duration | Description |
|------|--------|---------|-----|--------|------------|----------|-------------|
| Astralpfeil | 1 | 0 | - | DAMAGE | 5 | - | Ein astraler Energiestrahl |
| Astraler Schild | 1 | 1 | 6 | BUFF | +2 | 3 rounds | +2 Zauberverteidigung |
| Energielanze | 2 | 2 | 8 | DAMAGE | 9 | - | Ein gebündelter Energiestrahl |
| Schwächung | 2 | 1 | 7 | DEBUFF | -2 | 2 rounds | -2 auf Schadensstufe des Ziels |
| Arkane Rüstung | 3 | 2 | 9 | BUFF | +4 | 3 rounds | +4 Mystische Rüstung |

### Geisterbeschwörer Spells (Circle 1-3)

| Name | Circle | Threads | WD | Effect | Step/Value | Duration | Description |
|------|--------|---------|-----|--------|------------|----------|-------------|
| Geisterpfeil | 1 | 0 | - | DAMAGE | 5 | - | Ein Geschoss aus Geisterenergie |
| Seelenschild | 1 | 1 | 6 | BUFF | +2 | 3 rounds | +2 Mystische Rüstung |
| Todeshauch | 2 | 2 | 8 | DAMAGE | 8 | - | Ein kalter Hauch der Unterwelt |
| Geisterfesseln | 2 | 1 | 7 | DEBUFF | -2 | 2 rounds | -2 auf Initiative des Ziels |
| Seelenraub | 3 | 3 | 10 | DAMAGE | 11 | - | Entreißt dem Ziel Lebensenergie |

---

## Implementation Order

### Phase 1: Data Layer
1. Create `SpellEffectType` enum
2. Create `SpellDefinition` entity + repository
3. Create `CharacterSpell` entity + repository
4. Add `spells` list to `GameCharacter`
5. Add `preparingSpellId`, `threadsWoven`, `threadsRequired` to `CombatantState`
6. Add Fadenweben talent variants to `DataInitializer` (Elementarismus, Illusionismus, Magie, Geisterbeschwörung)
7. Add new discipline "Geisterbeschwörer" to `DataInitializer` (karmaStep=6, bw=3, td=4, talents: Spruchzauberei, Fadenmagie, Geisterbeschwörung, Standhalten, Meditation)
8. Seed example spells in `DataInitializer`
9. Update `migrateActionTypeConstraint()` with `THREADWEAVE`, `SPELL_CAST`
10. Add `THREADWEAVE` and `SPELL_CAST` to `ActionType` enum

### Phase 2: Backend Logic
11. Create DTOs: `ThreadweaveRequest`, `ThreadweaveResult`, `SpellCastRequest`, `SpellCastResult`
11. Create `SpellService` with `weaveThread()` and `castSpell()` methods
12. Wire `applyDamageToDefender()` for spell damage path (mystic armor, no dodge)
13. Add `cancelSpellPreparation()` method
14. Add controller endpoints (combat + character)
15. Add spell CRUD to `CharacterService` (assign/remove spells)

### Phase 3: Frontend — Character Sheet
16. Add TypeScript models for spells
17. Add spell service methods
18. Add "Sprüche" tab to character-sheet component
19. Add "Zauber hinzufügen" dialog (filterable by discipline)

### Phase 4: Frontend — Combat Tracker
20. Add threadweave dialog + result modal
21. Add spell cast dialog + result modal
22. Add spell preparation badge to combatant cards
23. Add "Faden weben" and "Zauber wirken" action buttons
24. Wire WebSocket updates for spell state changes

### Phase 5: Polish
25. Test all spell types (damage, buff, debuff, heal)
26. Verify knockdown triggers on spell damage
27. Verify thread state persists across rounds
28. Verify buff/debuff effects expire correctly
29. Test edge cases: defeated while weaving, 0-thread spells, self-targeting

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `SpellDefinition` is a separate entity, not part of `TalentDefinition` | Spells have fundamentally different properties (threads, weaving difficulty, effect types) that don't map cleanly to the talent system |
| Spell damage goes through `applyDamageToDefender()` | Reuses existing wound/knockdown logic; only armor type changes |
| Thread state lives on `CombatantState` | Threads are combat-specific and ephemeral, not character-level |
| Fadenweben variants are separate TalentDefinitions | Each discipline's weaving talent has its own rank; follows ED4 rules where Elementarismus ≠ Illusionismus |
| No dodge against spells | ED4 rule — hardcoded: skip the Ausweichen check entirely in spell path |
| No aggressive casting | ED4 rule — the aggressive attack toggle is disabled/hidden for spells |
| Buff/debuff spells reuse `ActiveEffect` + `ModifierEntry` | Already a mature system; spells just create effects with `sourceType = SPELL` |
| `SpellDefinition.discipline` is a String, not FK | Simpler, avoids circular dependencies, easy to query, future-proof for multi-discipline spells |
