// SockJS/STOMP werden beim Import von CombatTrackerComponent (über WebSocketService)
// geladen — im jsdom-Test gemockt, damit kein echter Netzwerk-Client gestartet wird.
jest.mock('sockjs-client', () => ({ __esModule: true, default: jest.fn() }));
jest.mock('@stomp/stompjs', () => ({ Client: class {}, }));

import { CombatTrackerComponent } from './combat-tracker.component';

/**
 * Unit-Tests für reine Hilfsmethoden im CombatTrackerComponent
 * (Amulette, Nachtreten, Waffen-Fertigkeiten). Kein TestBed.
 */
describe('CombatTrackerComponent — Amulette, Nachtreten, Waffen-Fertigkeiten', () => {
  let comp: CombatTrackerComponent;

  beforeEach(() => {
    comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent;
  });

  // --- Amulette ---

  it('amuletsOf() liefert nur geladene Amulette der passenden Art', () => {
    const c: any = { character: { equipment: [
      { id: 1, type: 'AMULET', charged: true,  amuletForSpell: false },
      { id: 2, type: 'AMULET', charged: false, amuletForSpell: false }, // entladen
      { id: 3, type: 'AMULET', charged: true,  amuletForSpell: true },  // Zauber
    ] } };
    expect(comp.amuletsOf(c, false).map((a: any) => a.id)).toEqual([1]);
    expect(comp.amuletsOf(c, true).map((a: any) => a.id)).toEqual([3]);
  });

  it('toggleAmuletMode() setzt, wechselt und entfernt den Modus', () => {
    const map: any = {};
    comp.toggleAmuletMode(map, 1, 'attack');
    expect(map[1]).toBe('attack');
    comp.toggleAmuletMode(map, 1, 'damage');
    expect(map[1]).toBe('damage');
    comp.toggleAmuletMode(map, 1, 'damage'); // gleicher Modus → abwählen
    expect(map[1]).toBeUndefined();
  });

  it('selectedAmuletIds() filtert nach Modus', () => {
    const map: any = { 1: 'attack', 2: 'damage', 3: 'attack' };
    expect(comp.selectedAmuletIds(map, 'attack').sort()).toEqual([1, 3]);
    expect(comp.selectedAmuletIds(map, 'damage')).toEqual([2]);
    expect(comp.selectedAmuletIds(undefined, 'attack')).toEqual([]);
  });

  // --- Nachtreten ---

  it('hasNachtretenTalent() erkennt das Talent', () => {
    const withTalent: any = { character: { talents: [{ talentDefinition: { name: 'Nachtreten' } }] } };
    const without: any = { character: { talents: [] } };
    expect(comp.hasNachtretenTalent(withTalent)).toBe(true);
    expect(comp.hasNachtretenTalent(without)).toBe(false);
  });

  it('nachtretenTargets() nur niedrigere Initiative, nicht selbst/besiegt', () => {
    (comp as any).session = { combatants: [
      { id: 1, initiative: 10, defeated: false },
      { id: 2, initiative: 5,  defeated: false },
      { id: 3, initiative: 12, defeated: false },
      { id: 4, initiative: 3,  defeated: true },
    ] };
    const actor: any = { id: 1, initiative: 10 };
    expect(comp.nachtretenTargets(actor).map((c: any) => c.id)).toEqual([2]);
  });

  // --- Waffen-Fertigkeiten ---

  it('weaponSkillsOf() liefert nur Nahkampf-/Projektilwaffen-Fertigkeiten', () => {
    const c: any = { character: { skills: [
      { skillDefinition: { name: 'Nahkampfwaffen' }, rank: 5 },
      { skillDefinition: { name: 'Reiten' }, rank: 3 },
      { skillDefinition: { name: 'Projektilwaffen' }, rank: 2 },
    ] } };
    expect(comp.weaponSkillsOf(c).map((s: any) => s.skillDefinition.name))
      .toEqual(['Nahkampfwaffen', 'Projektilwaffen']);
  });

  it('onAttackSourceChange() Fertigkeit setzt skillId und deaktiviert Karma', () => {
    (comp as any).attackDialog = { spendKarma: true }; // kein attacker → kein pushDialogState
    comp.onAttackSourceChange('s:14');
    const d: any = (comp as any).attackDialog;
    expect(d.skillId).toBe(14);
    expect(d.talentId).toBeUndefined();
    expect(d.spendKarma).toBe(false);
  });

  it('onAttackSourceChange() Talent setzt talentId und löscht skillId', () => {
    (comp as any).attackDialog = { spendKarma: false, skillId: 99 };
    comp.onAttackSourceChange('t:18');
    const d: any = (comp as any).attackDialog;
    expect(d.talentId).toBe(18);
    expect(d.skillId).toBeUndefined();
  });

  // --- Ein-/Zweihändige Waffen ---

  it('isTwoHandedWeaponSelected() erkennt zweihändige Waffe', () => {
    const dialog: any = { attacker: { character: { equipment: [
      { id: 1, type: 'WEAPON', twoHanded: true },
      { id: 2, type: 'WEAPON', twoHanded: false },
    ] } }, weaponId: 1 };
    expect(comp.isTwoHandedWeaponSelected(dialog)).toBe(true);
    dialog.weaponId = 2;
    expect(comp.isTwoHandedWeaponSelected(dialog)).toBe(false);
  });

  it('isTwoHandedWeaponSelected() false ohne Waffe', () => {
    const dialog: any = { attacker: { character: { equipment: [] } }, weaponId: undefined };
    expect(comp.isTwoHandedWeaponSelected(dialog)).toBe(false);
  });

  // --- Verteidigungs-Tooltip (alle Boni/Mali) ---

  it('defenseTooltip() listet Basis, Schild, Effekte und Gesamt', () => {
    const c: any = {
      character: {
        dexterity: 10, physicalDefenseBonus: 1,
        equipment: [{ type: 'SHIELD', active: true, physicalDefenseBonus: 3, name: 'Rundschild' }],
      },
      activeEffects: [
        { name: 'Defensive Haltung', modifiers: [{ targetStat: 'PHYSICAL_DEFENSE', operation: 'ADD', value: 3 }] },
        { name: 'Phantomkrieger', modifiers: [{ targetStat: 'PHYSICAL_DEFENSE', operation: 'ADD', value: 3 }] },
      ],
    };
    const tip = comp.defenseTooltip(c, 'PHYSICAL_DEFENSE');
    expect(tip).toContain('Basis: 6');               // (10+3)/2 = 6
    expect(tip).toContain('Konfig-Bonus: +1');
    expect(tip).toContain('Schild Rundschild: +3');
    expect(tip).toContain('Defensive Haltung: +3');
    expect(tip).toContain('Phantomkrieger: +3');
    expect(tip).toContain('Gesamt: 16');             // 6+1+3+3+3
  });

  it('defenseTooltip() markiert abgelegtes Schild als nicht zählend', () => {
    const c: any = {
      character: {
        dexterity: 10, physicalDefenseBonus: 0,
        equipment: [{ type: 'SHIELD', active: false, physicalDefenseBonus: 3, name: 'Rundschild' }],
      },
      activeEffects: [],
    };
    const tip = comp.defenseTooltip(c, 'PHYSICAL_DEFENSE');
    expect(tip).toContain('abgelegt, zählt nicht');
    expect(tip).toContain('Gesamt: 6'); // abgelegtes Schild zählt nicht
  });

  it('defenseTooltip() zeigt Mali (negative Werte) z.B. Niedergeschlagen', () => {
    const c: any = {
      character: { charisma: 10, socialDefenseBonus: 0, equipment: [] },
      activeEffects: [{ name: 'Niedergeschlagen', modifiers: [{ targetStat: 'SOCIAL_DEFENSE', operation: 'ADD', value: -3 }] }],
    };
    const tip = comp.defenseTooltip(c, 'SOCIAL_DEFENSE');
    expect(tip).toContain('Niedergeschlagen: -3');
    expect(tip).toContain('Gesamt: 3'); // (10+3)/2=6, -3 = 3
  });

  it('resolveActionType(): Projektilwaffen-Fertigkeit → RANGED, Nahkampfwaffen → MELEE', () => {
    (comp as any).attackDialog = { skillId: 15, attacker: { character: {
      skills: [{ skillDefinition: { id: 15, name: 'Projektilwaffen' }, rank: 2 }], talents: [] } } };
    expect((comp as any).resolveActionType()).toBe('RANGED_ATTACK');

    (comp as any).attackDialog = { skillId: 14, attacker: { character: {
      skills: [{ skillDefinition: { id: 14, name: 'Nahkampfwaffen' }, rank: 2 }], talents: [] } } };
    expect((comp as any).resolveActionType()).toBe('MELEE_ATTACK');
  });
});

describe('CombatTrackerComponent — Zauberauswahl nur aus Matrizen', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  function combatant(): any {
    return { character: {
      spells: [
        { spellDefinition: { id: 1, name: 'Feuerball', threads: 2 } },
        { spellDefinition: { id: 2, name: 'Eisnadeln', threads: 0 } },
        { spellDefinition: { id: 3, name: 'Lichtblitz', threads: 0 } }, // nicht in Matrize
      ],
      talents: [
        { talentDefinition: { name: 'Zaubermatritze' }, assignedSpell: { id: 1 } },
        { talentDefinition: { name: 'Erweiterte Matrize' }, assignedSpell: { id: 2 } },
        { talentDefinition: { name: 'Nahkampfwaffen' } },                 // keine Matrize
        { talentDefinition: { name: 'Zaubermatritze' }, assignedSpell: null }, // leere Matrize
      ],
    } };
  }

  it('matrixSpellIds() sammelt nur Zauber aus normalen + erweiterten Matrizen', () => {
    const ids = comp.matrixSpellIds(combatant());
    expect([...ids].sort()).toEqual([1, 2]);
  });

  it('spellsOf() bietet nur Matrix-Zauber an (Lichtblitz fehlt)', () => {
    expect(comp.spellsOf(combatant()).map(s => s.spellDefinition.id).sort()).toEqual([1, 2]);
  });

  it('readySpellsOf() zeigt 0-Faden-Zauber nur, wenn in Matrize', () => {
    // ohne preparingSpellId: nur threads===0 UND in Matrize → Eisnadeln(2), nicht Lichtblitz(3)
    const ready = comp.readySpellsOf(combatant());
    expect(ready.map(s => s.spellDefinition.id)).toEqual([2]);
  });

  it('readySpellsOf() bei fertig vorbereitetem Zauber nur diesen, wenn in Matrize', () => {
    const c = combatant();
    c.preparingSpellId = 1; c.threadsWoven = 2; c.threadsRequired = 2;
    expect(comp.readySpellsOf(c).map((s: any) => s.spellDefinition.id)).toEqual([1]);
  });

  it('ohne Matrizen werden keine Zauber angeboten', () => {
    const c: any = { character: { spells: [{ spellDefinition: { id: 9, threads: 0 } }], talents: [] } };
    expect(comp.spellsOf(c)).toEqual([]);
    expect(comp.readySpellsOf(c)).toEqual([]);
  });
});

describe('CombatTrackerComponent — Angriffsdialog nur Waffen-Angriffstalente', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  it('attackTalentsOf() liefert nur die vier Waffen-Angriffstalente, nach Rang sortiert', () => {
    const c: any = { character: { talents: [
      { talentDefinition: { id: 1, name: 'Nahkampfwaffen', attackTalent: true }, rank: 3 },
      { talentDefinition: { id: 2, name: 'Spruchzauberei', attackTalent: true }, rank: 5 }, // attackTalent, aber Zauber → raus
      { talentDefinition: { id: 3, name: 'Verspotten', attackTalent: false }, rank: 4 },     // Nicht-Angriff → raus
      { talentDefinition: { id: 4, name: 'Schwimmen', attackTalent: false }, rank: 2 },       // raus
      { talentDefinition: { id: 5, name: 'Waffenloser Kampf', attackTalent: true }, rank: 6 },
      { talentDefinition: { id: 6, name: 'Wurfwaffen', attackTalent: true }, rank: 1 },
      { talentDefinition: { id: 7, name: 'Projektilwaffen', attackTalent: true }, rank: 4 },
    ] } };
    expect(comp.attackTalentsOf(c).map((t: any) => t.talentDefinition.name))
      .toEqual(['Waffenloser Kampf', 'Projektilwaffen', 'Nahkampfwaffen', 'Wurfwaffen']);
  });

  it('attackTalentsOf() ist leer ohne passende Talente', () => {
    const c: any = { character: { talents: [{ talentDefinition: { name: 'Spruchzauberei', attackTalent: true }, rank: 5 }] } };
    expect(comp.attackTalentsOf(c)).toEqual([]);
  });
});
