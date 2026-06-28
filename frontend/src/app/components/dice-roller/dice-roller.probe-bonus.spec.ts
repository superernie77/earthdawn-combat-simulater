import { DiceRollerComponent } from './dice-roller.component';

/**
 * Unit-Tests für den Ausrüstungs-Probenbonus (GEAR, z.B. Leichte Stiefel) im Würfelwurf-Screen.
 * Kein TestBed — Komponente per Object.create() instanziiert.
 */
describe('DiceRollerComponent — Ausrüstungs-Probenbonus', () => {
  let comp: DiceRollerComponent;

  beforeEach(() => {
    comp = Object.create(DiceRollerComponent.prototype) as DiceRollerComponent;
  });

  it('equipmentProbeBonus() summiert GEAR-Boni nach Talentname (case-insensitive)', () => {
    (comp as any).activeChar = { equipment: [
      { type: 'GEAR', probeBonusTalentName: 'Heimlicher Schritt', probeBonusValue: 2 },
      { type: 'GEAR', probeBonusTalentName: 'heimlicher schritt', probeBonusValue: 1 },
      { type: 'GEAR', probeBonusTalentName: 'Klettern', probeBonusValue: 3 },
      { type: 'WEAPON' },
    ] };
    expect(comp.equipmentProbeBonus('Heimlicher Schritt')).toBe(3);
    expect(comp.equipmentProbeBonus('Klettern')).toBe(3);
    expect(comp.equipmentProbeBonus('Schwimmen')).toBe(0);
  });

  it('equipmentProbeBonus() ist 0 ohne Charakter/Ausrüstung', () => {
    (comp as any).activeChar = undefined;
    expect(comp.equipmentProbeBonus('Heimlicher Schritt')).toBe(0);
  });

  it('probeStepFor() addiert den Ausrüstungsbonus für das passende Talent', () => {
    (comp as any).activeChar = { dexterity: 16, wounds: 0, equipment: [
      { type: 'GEAR', probeBonusTalentName: 'Heimlicher Schritt', probeBonusValue: 2 },
    ] };
    // attrToStep(16) = 7, Rang 3, + 2 Stiefel = 12
    expect(comp.probeStepFor('DEXTERITY', 3, 'Heimlicher Schritt')).toBe(12);
    // kein passender Name → kein Bonus = 10
    expect(comp.probeStepFor('DEXTERITY', 3, 'Klettern')).toBe(10);
    // ohne Name → kein Bonus = 10
    expect(comp.probeStepFor('DEXTERITY', 3)).toBe(10);
  });

  it('probeStepFor() zieht Wunden ab und klemmt auf min. 1', () => {
    (comp as any).activeChar = { dexterity: 16, wounds: 2, equipment: [] };
    // 7 + 3 - 2 = 8
    expect(comp.probeStepFor('DEXTERITY', 3)).toBe(8);
  });
});
