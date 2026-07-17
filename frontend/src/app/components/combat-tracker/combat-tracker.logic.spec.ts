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

describe('CombatTrackerComponent — Waffen nach Angriffstalent gefiltert', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  function combatant(): any {
    return { character: {
      talents: [
        { talentDefinition: { id: 1, name: 'Nahkampfwaffen' }, rank: 3 },
        { talentDefinition: { id: 2, name: 'Projektilwaffen' }, rank: 2 },
      ],
      skills: [{ skillDefinition: { id: 5, name: 'Wurfwaffen' }, rank: 4 }],
      equipment: [
        { id: 10, type: 'WEAPON', name: 'Schwert', damageBonus: 3, attackTalentName: 'Nahkampfwaffen' },
        { id: 11, type: 'WEAPON', name: 'Bogen', damageBonus: 2, attackTalentName: 'Projektilwaffen' },
        { id: 12, type: 'WEAPON', name: 'Dolch', damageBonus: 1 }, // ohne Zuordnung
      ],
    } };
  }

  it('zeigt nur passende + unzugeordnete Waffen für das gewählte Talent', () => {
    const c = combatant();
    (comp as any).attackDialog = { attacker: c, talentId: 1, skillId: undefined }; // Nahkampfwaffen
    expect(comp.attackWeaponsFor(c).map((w: any) => w.name)).toEqual(['Schwert', 'Dolch']);
  });

  it('filtert nach gewählter Fertigkeit (Wurfwaffen → nur unzugeordnete)', () => {
    const c = combatant();
    (comp as any).attackDialog = { attacker: c, talentId: undefined, skillId: 5 }; // Wurfwaffen
    expect(comp.attackWeaponsFor(c).map((w: any) => w.name)).toEqual(['Dolch']);
  });

  it('ohne gewähltes Talent werden alle Waffen angeboten', () => {
    const c = combatant();
    (comp as any).attackDialog = { attacker: c, talentId: undefined, skillId: undefined };
    expect(comp.attackWeaponsFor(c).map((w: any) => w.name)).toEqual(['Schwert', 'Bogen', 'Dolch']);
  });
});

describe('CombatTrackerComponent — Verängstigen', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  it('hasFearTalent() erkennt das Talent Verängstigen', () => {
    const mit: any = { character: { talents: [{ talentDefinition: { name: 'Verängstigen' }, rank: 3 }] } };
    const ohne: any = { character: { talents: [{ talentDefinition: { name: 'Verspotten' }, rank: 3 }] } };
    expect(comp.hasFearTalent(mit)).toBe(true);
    expect(comp.hasFearTalent(ohne)).toBe(false);
  });

  it('isFeared() und fearResistTn() lesen den Verängstigt-Effekt', () => {
    const feared: any = { activeEffects: [{ name: 'Verängstigt', resistTargetNumber: 8, modifiers: [] }] };
    const clean: any = { activeEffects: [{ name: 'Verspottet', modifiers: [] }] };
    expect(comp.isFeared(feared)).toBe(true);
    expect(comp.fearResistTn(feared)).toBe(8);
    expect(comp.isFeared(clean)).toBe(false);
    expect(comp.fearResistTn(clean)).toBe(0);
  });

  it('fearTargets() schließt Anwender und Besiegte aus', () => {
    (comp as any).fearDialog = { actor: { id: 1 } };
    (comp as any).session = { combatants: [
      { id: 1, defeated: false }, // Anwender
      { id: 2, defeated: false },
      { id: 3, defeated: true },  // besiegt
    ] };
    expect(comp.fearTargets().map((c: any) => c.id)).toEqual([2]);
  });

  it('resistFear() ruft den Service mit Session- und Kombattanten-Id auf', () => {
    (comp as any).session = { id: 7 };
    const resistFear = jest.fn().mockReturnValue({ subscribe: () => {} });
    (comp as any).combatService = { resistFear };
    comp.resistFear({ id: 42 } as any);
    expect(resistFear).toHaveBeenCalledWith(7, 42);
  });
});

describe('CombatTrackerComponent — Magie neutralisieren', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => {
    comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent;
    (comp as any).cn = (c: any) => c.character?.name ?? '?';
  });

  it('hasNeutralizeMagicTalent() erkennt das Talent', () => {
    const mit: any = { character: { talents: [{ talentDefinition: { name: 'Magie neutralisieren' }, rank: 4 }] } };
    const ohne: any = { character: { talents: [{ talentDefinition: { name: 'Verängstigen' }, rank: 4 }] } };
    expect(comp.hasNeutralizeMagicTalent(mit)).toBe(true);
    expect(comp.hasNeutralizeMagicTalent(ohne)).toBe(false);
  });

  it('allActiveEffects() sammelt Effekte aller Kombattanten mit key combatantId:effectId', () => {
    (comp as any).session = { combatants: [
      { id: 1, character: { name: 'Kaelen' }, activeEffects: [
        { id: 500, name: 'Verängstigt', remainingRounds: 3 },
        { id: 501, name: 'Segen', remainingRounds: -1 },
      ] },
      { id: 2, character: { name: 'Ork' }, activeEffects: [{ id: 502, name: 'Bedrängt', remainingRounds: 1 }] },
      { id: 3, character: { name: 'Leer' }, activeEffects: [] },
    ] };
    const all = comp.allActiveEffects();
    expect(all.map(e => e.key)).toEqual(['1:500', '1:501', '2:502']);
    expect(all[0].combatantName).toBe('Kaelen');
    expect(all[2].name).toBe('Bedrängt');
  });

  it('allActiveEffects() überspringt Effekte ohne id und ist leer ohne Session', () => {
    (comp as any).session = { combatants: [
      { id: 1, character: { name: 'X' }, activeEffects: [{ name: 'Ohne Id', remainingRounds: 1 }] },
    ] };
    expect(comp.allActiveEffects()).toEqual([]);
    (comp as any).session = undefined;
    expect(comp.allActiveEffects()).toEqual([]);
  });

  it('neutralizeActor() findet den Anwender aus dem synchronisierten Dialog', () => {
    (comp as any).session = { combatants: [{ id: 1, character: { name: 'A' } }, { id: 2, character: { name: 'B' } }] };
    (comp as any).neutralizeSelectModal = { open: true, actorCombatantId: 2 };
    expect(comp.neutralizeActor()?.id).toBe(2);
    (comp as any).neutralizeSelectModal = { open: true, actorCombatantId: undefined };
    expect(comp.neutralizeActor()).toBeUndefined();
  });

  it('performNeutralizeMagic() zerlegt die Auswahl und schickt Ziel, Effekt und Stufe', () => {
    (comp as any).session = { id: 7, combatants: [] };
    (comp as any).neutralizeSelectModal = {
      open: true, actorCombatantId: 1, selection: '2:502', effectLevel: 8, spendKarma: true
    };
    const performNeutralizeMagic = jest.fn().mockReturnValue({ subscribe: () => {} });
    (comp as any).combatService = { performNeutralizeMagic };

    comp.performNeutralizeMagic();

    expect(performNeutralizeMagic).toHaveBeenCalledWith(7, {
      sessionId: 7, actorCombatantId: 1, targetCombatantId: 2,
      effectId: 502, effectLevel: 8, bonusSteps: 0, spendKarma: true
    });
  });

  it('performNeutralizeMagic() tut nichts ohne Auswahl', () => {
    (comp as any).session = { id: 7 };
    (comp as any).neutralizeSelectModal = { open: true, actorCombatantId: 1, selection: undefined, effectLevel: 5 };
    const performNeutralizeMagic = jest.fn();
    (comp as any).combatService = { performNeutralizeMagic };
    comp.performNeutralizeMagic();
    expect(performNeutralizeMagic).not.toHaveBeenCalled();
  });

  /**
   * Regression: allActiveEffects() erzeugt neue Objekte. Wird es im Template (*ngFor) aufgerufen,
   * baut mat-select die Optionen in jedem Change-Detection-Zyklus neu auf → Endlosschleife,
   * die den Browser einfriert. Die Liste muss daher beim Öffnen einmalig gesnapshottet werden.
   */
  it('NEUTRALIZE_MAGIC_SELECT snapshottet die Effektliste beim Öffnen', () => {
    (comp as any).session = { combatants: [
      { id: 1, character: { name: 'Kaelen' }, activeEffects: [{ id: 500, name: 'Verängstigt', remainingRounds: 3 }] },
      { id: 2, character: { name: 'Ork' }, activeEffects: [{ id: 502, name: 'Bedrängt', remainingRounds: 1 }] },
    ] };
    // Modale, die closeAllResultModals() ungeschützt anfasst
    for (const m of ['resultModal', 'initiativeModal', 'tigersprungModal', 'lufttanzModal']) {
      (comp as any)[m] = { open: false };
    }
    (comp as any).neutralizeSelectModal = { open: false, effects: [], effectLevel: 5, spendKarma: false };

    (comp as any).openLocalModalForType('NEUTRALIZE_MAGIC_SELECT', { actorCombatantId: 1, actorName: 'Kaelen', rank: 4 });

    const m = (comp as any).neutralizeSelectModal;
    expect(m.open).toBe(true);
    expect(m.actorName).toBe('Kaelen');
    expect(m.rank).toBe(4);
    expect(m.effects.map((e: any) => e.key)).toEqual(['1:500', '2:502']); // Snapshot vorhanden
    expect(m.selection).toBeUndefined();
  });

  it('trackByEffectKey() liefert den stabilen key', () => {
    expect(comp.trackByEffectKey(0, { key: '1:500' } as any)).toBe('1:500');
  });
});

describe('CombatTrackerComponent — Zusatzfäden', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  const OPT_STEP = { label: 'Wirkung Verstärken (Wirkungsstufe +2)', type: 'EFFECT_STEP', value: 2 };
  const OPT_DISPLAY = { label: 'Reichweite Erhöhen (+10 Schritt)', type: 'DISPLAY', value: 0 };

  /** Illusionist mit Illusionismus-Rang und einem Zauber in der Matrize. */
  function caster(opts: {
    threads: number;
    threadOptions?: any[];
    weavingRank?: number;
    enhancedMatrix?: boolean;
    preparingSpellId?: number;
    threadsWoven?: number;
    threadsRequired?: number;
    extraThreadChoices?: string;
  }): any {
    const spellDef = {
      id: 50, name: 'Blitz', threads: opts.threads,
      weavingDifficulty: 5, threadOptions: opts.threadOptions
    };
    const talents: any[] = [
      { talentDefinition: { name: 'Illusionismus' }, rank: opts.weavingRank ?? 4 },
      { talentDefinition: { name: 'Zaubermatritze' }, assignedSpell: spellDef, rank: 1 }
    ];
    if (opts.enhancedMatrix) {
      talents.push({ talentDefinition: { name: 'Erweiterte Matrize' }, assignedSpell: spellDef, rank: 1 });
    }
    return {
      id: 10,
      preparingSpellId: opts.preparingSpellId,
      threadsWoven: opts.threadsWoven ?? 0,
      threadsRequired: opts.threadsRequired ?? 0,
      extraThreadChoices: opts.extraThreadChoices,
      character: { discipline: { name: 'Illusionist' }, talents, spells: [{ spellDefinition: spellDef }] }
    };
  }

  function openWith(c: any, extraOptionIndex?: number) {
    (comp as any).threadweaveDialog = { open: true, caster: c, spellId: 50, spendKarma: false, extraOptionIndex };
  }

  // --- Erkennung: Pflicht- vs. Zusatzfaden ---

  it('threadweaveIsExtra() ist false, solange Pflichtfäden offen sind', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP], preparingSpellId: 50, threadsWoven: 1, threadsRequired: 2 }));
    expect(comp.threadweaveIsExtra()).toBe(false);
  });

  it('threadweaveIsExtra() ist true, sobald alle Pflichtfäden gewoben sind', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP], preparingSpellId: 50, threadsWoven: 2, threadsRequired: 2 }));
    expect(comp.threadweaveIsExtra()).toBe(true);
  });

  it('threadweaveIsExtra() ist true bei einem Sofortzauber (0 Fäden) vor der Vorbereitung', () => {
    openWith(caster({ threads: 0, threadOptions: [OPT_STEP] }));
    expect(comp.threadweaveIsExtra()).toBe(true);
  });

  it('threadweaveIsExtra() ist false bei einem 2-Faden-Zauber vor der Vorbereitung', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP] }));
    expect(comp.threadweaveIsExtra()).toBe(false);
  });

  it('threadweaveIsExtra() beachtet die erweiterte Matrize (1 Faden vorgewoben)', () => {
    // 1-Faden-Zauber in erweiterter Matrize → 0 Pflichtfäden → erster Faden ist bereits Zusatzfaden
    openWith(caster({ threads: 1, threadOptions: [OPT_STEP], enhancedMatrix: true }));
    expect(comp.threadweaveIsExtra()).toBe(true);
  });

  // --- Obergrenze ---

  it('weavingRankOf() liest den Rang des Fadenweben-Talents der Disziplin', () => {
    expect(comp.weavingRankOf(caster({ threads: 2, weavingRank: 6 }))).toBe(6);
  });

  it('weavingRankOf() ist 0 ohne passende Disziplin', () => {
    const c = caster({ threads: 2 });
    c.character.discipline = { name: 'Krieger' };
    expect(comp.weavingRankOf(c)).toBe(0);
  });

  it('extraThreadCountOf() zählt die CSV-Einträge', () => {
    expect(comp.extraThreadCountOf(caster({ threads: 2 }))).toBe(0);
    expect(comp.extraThreadCountOf(caster({ threads: 2, extraThreadChoices: '0' }))).toBe(1);
    expect(comp.extraThreadCountOf(caster({ threads: 2, extraThreadChoices: '0,1,0' }))).toBe(3);
  });

  it('threadweaveExtraExhausted() greift bei erreichtem Fadenweben-Rang', () => {
    openWith(caster({
      threads: 0, threadOptions: [OPT_STEP], weavingRank: 2,
      preparingSpellId: 50, extraThreadChoices: '0,0'
    }));
    expect(comp.threadweaveExtraExhausted()).toBe(true);
  });

  it('threadweaveExtraExhausted() ist false unterhalb des Rangs', () => {
    openWith(caster({
      threads: 0, threadOptions: [OPT_STEP], weavingRank: 3,
      preparingSpellId: 50, extraThreadChoices: '0,0'
    }));
    expect(comp.threadweaveExtraExhausted()).toBe(false);
  });

  // --- Button-Sperre ---

  it('threadweaveBlocked() ist false für einen Pflichtfaden ohne Option', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP], preparingSpellId: 50, threadsWoven: 0, threadsRequired: 2 }));
    expect(comp.threadweaveBlocked()).toBe(false);
  });

  it('threadweaveBlocked() sperrt einen Zusatzfaden ohne gewählte Option', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP], preparingSpellId: 50, threadsWoven: 2, threadsRequired: 2 }));
    expect(comp.threadweaveBlocked()).toBe(true);
  });

  it('threadweaveBlocked() gibt einen Zusatzfaden mit Option frei', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP], preparingSpellId: 50, threadsWoven: 2, threadsRequired: 2 }), 0);
    expect(comp.threadweaveBlocked()).toBe(false);
  });

  it('threadweaveBlocked() lässt Option-Index 0 zu (kein Falsy-Bug)', () => {
    openWith(caster({ threads: 0, threadOptions: [OPT_STEP] }), 0);
    expect(comp.threadweaveBlocked()).toBe(false);
  });

  it('threadweaveBlocked() sperrt Zusatzfäden bei einem Zauber ohne Optionen', () => {
    openWith(caster({ threads: 2, threadOptions: [], preparingSpellId: 50, threadsWoven: 2, threadsRequired: 2 }), 0);
    expect(comp.threadweaveBlocked()).toBe(true);
  });

  it('threadweaveBlocked() sperrt bei erschöpftem Fadenweben-Rang', () => {
    openWith(caster({
      threads: 0, threadOptions: [OPT_STEP], weavingRank: 1,
      preparingSpellId: 50, extraThreadChoices: '0'
    }), 0);
    expect(comp.threadweaveBlocked()).toBe(true);
  });

  // --- Optionen ---

  it('threadweaveOptions() liefert die Optionen des gewählten Zaubers', () => {
    openWith(caster({ threads: 2, threadOptions: [OPT_STEP, OPT_DISPLAY] }));
    expect(comp.threadweaveOptions()).toEqual([OPT_STEP, OPT_DISPLAY]);
  });

  it('threadweaveOptions() ist leer, wenn der Zauber keine kennt', () => {
    openWith(caster({ threads: 2 }));
    expect(comp.threadweaveOptions()).toEqual([]);
  });

  it('trackByOptionIndex() liefert den Index', () => {
    expect(comp.trackByOptionIndex(3)).toBe(3);
  });
});

describe('CombatTrackerComponent — Kampfkarte: Reichweiten-Filter', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  function combatant(id: number, q: number | null, r: number | null, extra: any = {}): any {
    return { id, mapQ: q, mapR: r, defeated: false,
      character: { name: 'C' + id, talents: [], skills: [], spells: [], equipment: [] }, ...extra };
  }

  function setup(mapEnabled: boolean, combatants: any[]): void {
    (comp as any).session = { id: 1, mapEnabled, mapWidth: 24, mapHeight: 16, combatants };
    (comp as any).attackDialog = { open: false };
  }

  it('mapDistanceBetween liefert die Hexdistanz platzierter Kombattanten', () => {
    const a = combatant(1, 2, 2), b = combatant(2, 5, 2);
    setup(true, [a, b]);
    expect(comp.mapDistanceBetween(a, b)).toBe(3);
  });

  it('mapDistanceBetween ist null ohne Karte oder ohne Platzierung', () => {
    const a = combatant(1, 2, 2), b = combatant(2, null, null);
    setup(false, [a, b]);
    expect(comp.mapDistanceBetween(a, combatant(2, 5, 2))).toBeNull();
    setup(true, [a, b]);
    expect(comp.mapDistanceBetween(a, b)).toBeNull();
  });

  it('possibleTargets(actor) filtert Nahkampf auf angrenzende Felder', () => {
    const actor = combatant(1, 2, 2);
    const adjacent = combatant(2, 3, 2);
    const far = combatant(3, 6, 2);
    setup(true, [actor, adjacent, far]);
    const targets = comp.possibleTargets(actor);
    expect(targets.map((t: any) => t.id)).toEqual([2]);
  });

  it('possibleTargets(actor) lässt unplatzierte Ziele wählbar', () => {
    const actor = combatant(1, 2, 2);
    const unplaced = combatant(2, null, null);
    setup(true, [actor, unplaced]);
    expect(comp.possibleTargets(actor).map((t: any) => t.id)).toEqual([2]);
  });

  it('possibleTargets(actor) filtert nicht, wenn die Karte aus ist oder der Akteur unplatziert', () => {
    const far = combatant(3, 9, 9);
    const actorOff = combatant(1, 2, 2);
    setup(false, [actorOff, far]);
    expect(comp.possibleTargets(actorOff).map((t: any) => t.id)).toEqual([3]);
    const actorUnplaced = combatant(1, null, null);
    setup(true, [actorUnplaced, far]);
    expect(comp.possibleTargets(actorUnplaced).map((t: any) => t.id)).toEqual([3]);
  });

  it('Angriffsdialog: Fernkampf nutzt die Weit-Reichweite der gewählten Waffe', () => {
    const bow = { id: 7, name: 'Bogen', type: 'WEAPON', attackTalentName: 'Projektilwaffen',
                  rangeShort: 3, rangeMedium: 6, rangeLong: 10 };
    const actor = combatant(1, 0, 0, { character: { name: 'A',
      talents: [{ talentDefinition: { id: 5, name: 'Projektilwaffen' }, rank: 3 }],
      skills: [], spells: [], equipment: [bow] } });
    const near = combatant(2, 8, 0);   // Distanz 8 ≤ 10
    const far = combatant(3, 15, 0);   // Distanz 15 > 10
    setup(true, [actor, near, far]);
    (comp as any).attackDialog = { open: true, attacker: actor, weaponId: 7,
      talentId: 5, attackSource: 't:5' };
    const targets = comp.possibleTargets();
    expect(targets.map((t: any) => t.id)).toEqual([2]);
  });

  it('spellTargets filtert nach Zauberreichweite', () => {
    const blitz = { spellDefinition: { id: 50, name: 'Blitz', threads: 0, effectType: 'DAMAGE',
      rangeHexes: 5, requiresTarget: false } };
    const caster = combatant(1, 0, 0, { character: { name: 'Z',
      talents: [{ talentDefinition: { name: 'Zaubermatritze' }, assignedSpell: blitz.spellDefinition, rank: 1 }],
      skills: [], spells: [blitz], equipment: [] } });
    const near = combatant(2, 4, 0);
    const far = combatant(3, 9, 0);
    setup(true, [caster, near, far]);
    (comp as any).spellCastDialog = { open: true, caster, spellId: 50 };
    expect(comp.spellTargets().map((t: any) => t.id)).toEqual([2]);
  });
});

describe('CombatTrackerComponent — Kampfprotokoll-Reihenfolge', () => {
  let comp: CombatTrackerComponent;
  beforeEach(() => { comp = Object.create(CombatTrackerComponent.prototype) as CombatTrackerComponent; });

  it('toLogEntries zeigt die neuesten Einträge oben — unabhängig von der Eingabereihenfolge', () => {
    const desc = [{ id: 3, description: 'neu' }, { id: 2 }, { id: 1, description: 'alt' }];
    const asc = [{ id: 1, description: 'alt' }, { id: 2 }, { id: 3, description: 'neu' }];
    for (const input of [desc, asc]) {
      const out = (comp as any).toLogEntries(input);
      expect(out.map((e: any) => e.id)).toEqual([3, 2, 1]);
    }
  });

  it('toLogEntries parst rollDetailsJson und übersteht kaputtes JSON', () => {
    const out = (comp as any).toLogEntries([
      { id: 2, rollDetailsJson: '{"hit":true}' },
      { id: 1, rollDetailsJson: 'kaputt{' }
    ]);
    expect(out[0].details).toEqual({ hit: true });
    expect(out[1].details).toBeNull();
  });
});
