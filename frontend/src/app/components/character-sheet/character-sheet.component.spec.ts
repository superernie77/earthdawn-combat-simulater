import { CharacterSheetComponent } from './character-sheet.component';
import { Equipment } from '../../models/character.model';

/**
 * Unit-Tests für reine Berechnungsmethoden in CharacterSheetComponent.
 * Kein TestBed — Komponente wird per Object.create() instanziiert,
 * relevante Felder werden direkt gesetzt.
 */
describe('CharacterSheetComponent — shieldKVBonus', () => {
  let comp: CharacterSheetComponent;

  beforeEach(() => {
    comp = Object.create(CharacterSheetComponent.prototype) as CharacterSheetComponent;
  });

  function withEquipment(...items: Partial<Equipment>[]) {
    (comp as any).character = { equipment: items };
  }

  it('returns 0 when no equipment', () => {
    withEquipment();
    expect(comp.shieldKVBonus()).toBe(0);
  });

  it('returns 0 when no shields present', () => {
    withEquipment({ type: 'WEAPON', physicalDefenseBonus: 5 });
    expect(comp.shieldKVBonus()).toBe(0);
  });

  it('returns physicalDefenseBonus of a single shield', () => {
    withEquipment({ type: 'SHIELD', physicalDefenseBonus: 3 });
    expect(comp.shieldKVBonus()).toBe(3);
  });

  it('sums physicalDefenseBonus across multiple shields', () => {
    withEquipment(
      { type: 'SHIELD', physicalDefenseBonus: 2 },
      { type: 'SHIELD', physicalDefenseBonus: 3 },
    );
    expect(comp.shieldKVBonus()).toBe(5);
  });

  it('ignores shields with zero bonus', () => {
    withEquipment(
      { type: 'SHIELD', physicalDefenseBonus: 0 },
      { type: 'SHIELD', physicalDefenseBonus: 4 },
    );
    expect(comp.shieldKVBonus()).toBe(4);
  });

  it('ignores shields with undefined bonus', () => {
    withEquipment({ type: 'SHIELD', physicalDefenseBonus: undefined as any });
    expect(comp.shieldKVBonus()).toBe(0);
  });

  it('returns 0 when character is undefined', () => {
    (comp as any).character = undefined;
    expect(comp.shieldKVBonus()).toBe(0);
  });
});

// ---------------------------------------------------------------------------

describe('CharacterSheetComponent — derivedNote', () => {
  let comp: CharacterSheetComponent;

  beforeEach(() => {
    comp = Object.create(CharacterSheetComponent.prototype) as CharacterSheetComponent;
    (comp as any).character = { equipment: [], willpower: 10, holzhautBonus: 0 };
    (comp as any).derived = { holzhautBonus: 0 };
  });

  // physicalDefense
  it('returns null for physicalDefense when no shields', () => {
    expect(comp.derivedNote('physicalDefense')).toBeNull();
  });

  it('returns "+N Schild" for physicalDefense when shield bonus > 0', () => {
    (comp as any).character.equipment = [{ type: 'SHIELD', physicalDefenseBonus: 3 }];
    expect(comp.derivedNote('physicalDefense')).toBe('+3 Schild');
  });

  it('returns null for physicalDefense when all shields have zero bonus', () => {
    (comp as any).character.equipment = [{ type: 'SHIELD', physicalDefenseBonus: 0 }];
    expect(comp.derivedNote('physicalDefense')).toBeNull();
  });

  it('sums multiple shields in physicalDefense note', () => {
    (comp as any).character.equipment = [
      { type: 'SHIELD', physicalDefenseBonus: 2 },
      { type: 'SHIELD', physicalDefenseBonus: 1 },
    ];
    expect(comp.derivedNote('physicalDefense')).toBe('+3 Schild');
  });

  // mysticArmor — existing behaviour, should not regress
  it('returns null for mysticArmor when WIL < 5', () => {
    (comp as any).character.willpower = 4;
    expect(comp.derivedNote('mysticArmor')).toBeNull();
  });

  it('returns "+1 aus WIL" for mysticArmor when WIL = 5', () => {
    (comp as any).character.willpower = 5;
    expect(comp.derivedNote('mysticArmor')).toBe('+1 aus WIL');
  });

  // holzhaut — existing behaviour
  it('returns null for unconsciousnessRating when holzhautBonus = 0', () => {
    expect(comp.derivedNote('unconsciousnessRating')).toBeNull();
  });

  it('returns holzhaut note when holzhautBonus > 0', () => {
    (comp as any).derived = { holzhautBonus: 5 };
    expect(comp.derivedNote('unconsciousnessRating')).toBe('+5 Holzhaut');
    expect(comp.derivedNote('deathRating')).toBe('+5 Holzhaut');
  });

  // unbekannte Keys
  it('returns null for unknown keys', () => {
    expect(comp.derivedNote('initiativeStep')).toBeNull();
    expect(comp.derivedNote('recoveryStep')).toBeNull();
  });
});

// ---------------------------------------------------------------------------

describe('CharacterSheetComponent — derivedTooltip', () => {
  let comp: CharacterSheetComponent;

  beforeEach(() => {
    comp = Object.create(CharacterSheetComponent.prototype) as CharacterSheetComponent;
    (comp as any).character = { equipment: [], willpower: 10, holzhautBonus: 0 };
    (comp as any).derived = { holzhautBonus: 0 };
  });

  // physicalDefense
  it('returns empty string for physicalDefense when no shield bonus', () => {
    expect(comp.derivedTooltip('physicalDefense')).toBe('');
  });

  it('returns tooltip with shield name and bonus', () => {
    (comp as any).character.equipment = [
      { type: 'SHIELD', name: 'Rundschild', physicalDefenseBonus: 3 },
    ];
    expect(comp.derivedTooltip('physicalDefense')).toBe('Schildbonus auf KV: Rundschild +3');
  });

  it('lists all shields with bonus in tooltip', () => {
    (comp as any).character.equipment = [
      { type: 'SHIELD', name: 'Turnierschild', physicalDefenseBonus: 2 },
      { type: 'SHIELD', name: 'Rundschild', physicalDefenseBonus: 1 },
    ];
    expect(comp.derivedTooltip('physicalDefense')).toBe(
      'Schildbonus auf KV: Turnierschild +2, Rundschild +1'
    );
  });

  it('excludes shields with zero KV bonus from tooltip', () => {
    (comp as any).character.equipment = [
      { type: 'SHIELD', name: 'Dekoschild', physicalDefenseBonus: 0 },
      { type: 'SHIELD', name: 'Rundschild', physicalDefenseBonus: 2 },
    ];
    expect(comp.derivedTooltip('physicalDefense')).toBe('Schildbonus auf KV: Rundschild +2');
  });

  // mysticArmor — existing behaviour, should not regress
  it('returns WIL tooltip for mysticArmor', () => {
    (comp as any).character.willpower = 10;
    expect(comp.derivedTooltip('mysticArmor')).toContain('Willenskraft 10');
    expect(comp.derivedTooltip('mysticArmor')).toContain('2');
  });

  // holzhaut — existing behaviour
  it('returns empty string for unconsciousnessRating when no holzhaut', () => {
    expect(comp.derivedTooltip('unconsciousnessRating')).toBe('');
  });

  it('returns holzhaut tooltip when bonus > 0', () => {
    (comp as any).derived = { holzhautBonus: 4 };
    expect(comp.derivedTooltip('unconsciousnessRating')).toContain('Holzhaut-Bonus von +4');
  });

  // unbekannte Keys
  it('returns empty string for unknown keys', () => {
    expect(comp.derivedTooltip('initiativeStep')).toBe('');
  });
});
