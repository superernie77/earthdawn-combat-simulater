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

  it('resolveActionType(): Projektilwaffen-Fertigkeit → RANGED, Nahkampfwaffen → MELEE', () => {
    (comp as any).attackDialog = { skillId: 15, attacker: { character: {
      skills: [{ skillDefinition: { id: 15, name: 'Projektilwaffen' }, rank: 2 }], talents: [] } } };
    expect((comp as any).resolveActionType()).toBe('RANGED_ATTACK');

    (comp as any).attackDialog = { skillId: 14, attacker: { character: {
      skills: [{ skillDefinition: { id: 14, name: 'Nahkampfwaffen' }, rank: 2 }], talents: [] } } };
    expect((comp as any).resolveActionType()).toBe('MELEE_ATTACK');
  });
});
