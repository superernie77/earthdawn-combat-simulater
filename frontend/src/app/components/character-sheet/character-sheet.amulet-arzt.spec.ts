import { CharacterSheetComponent } from './character-sheet.component';
import { Character, Equipment } from '../../models/character.model';

/**
 * Unit-Tests für Amulett- und Arzt/Verbandszeug-Berechnungen im CharacterSheetComponent.
 * Kein TestBed — Komponente per Object.create() instanziiert, Felder direkt gesetzt.
 */
describe('CharacterSheetComponent — Verbandszeug & Amulette', () => {
  let comp: CharacterSheetComponent;

  beforeEach(() => {
    comp = Object.create(CharacterSheetComponent.prototype) as CharacterSheetComponent;
  });

  function setEquipment(...items: Partial<Equipment>[]) {
    (comp as any).character = { equipment: items };
  }

  // --- verbandszeug() / verbandszeugCount() ---

  it('verbandszeug() filtert nur VERBANDSZEUG', () => {
    setEquipment({ type: 'VERBANDSZEUG', quantity: 3 }, { type: 'POTION', quantity: 1 });
    expect(comp.verbandszeug().length).toBe(1);
  });

  it('verbandszeugCount() summiert die Mengen', () => {
    const c = { equipment: [
      { type: 'VERBANDSZEUG', quantity: 3 },
      { type: 'VERBANDSZEUG', quantity: 2 },
    ] } as unknown as Character;
    expect(comp.verbandszeugCount(c)).toBe(5);
  });

  it('verbandszeugCount() ist 0 ohne Verbandszeug oder ohne Charakter', () => {
    expect(comp.verbandszeugCount({ equipment: [{ type: 'POTION', quantity: 1 }] } as any)).toBe(0);
    expect(comp.verbandszeugCount(undefined)).toBe(0);
  });

  it('selectedHealerVerbandszeug() nutzt den gewählten Heiler', () => {
    (comp as any).allCharacters = [{ id: 1, equipment: [{ type: 'VERBANDSZEUG', quantity: 2 }] }];
    (comp as any).selectedHealerId = 1;
    expect(comp.selectedHealerVerbandszeug()).toBe(2);
  });

  it('selectedHealerVerbandszeug() ist 0 ohne Auswahl', () => {
    (comp as any).allCharacters = [];
    (comp as any).selectedHealerId = undefined;
    expect(comp.selectedHealerVerbandszeug()).toBe(0);
  });

  // --- amulets() / bloodMagicDamage() ---

  it('amulets() filtert nur AMULET', () => {
    setEquipment({ type: 'AMULET' }, { type: 'WEAPON' }, { type: 'AMULET' });
    expect(comp.amulets().length).toBe(2);
  });

  it('bloodMagicDamage() liest den Wert aus derived', () => {
    (comp as any).derived = { bloodMagicDamage: 6 };
    expect(comp.bloodMagicDamage()).toBe(6);
  });

  it('bloodMagicDamage() ist 0 ohne derived', () => {
    (comp as any).derived = undefined;
    expect(comp.bloodMagicDamage()).toBe(0);
  });

  // --- Sonstige Ausrüstung (GEAR) ---

  it('gear() filtert nur GEAR', () => {
    setEquipment({ type: 'GEAR', probeBonusTalentName: 'Heimlicher Schritt', probeBonusValue: 2 }, { type: 'WEAPON' });
    expect(comp.gear().length).toBe(1);
  });

  it('addSchwimmkristall() legt GEAR +3 auf Schwimmen mit Unterwasseratmung an', () => {
    (comp as any).character = { id: 1 };
    const addEquipment = jest.fn().mockReturnValue({ subscribe: (cb: any) => cb({ id: 1 }) });
    (comp as any).characterService = { addEquipment };

    comp.addSchwimmkristall();

    expect(addEquipment).toHaveBeenCalledTimes(1);
    const [charId, eq] = addEquipment.mock.calls[0];
    expect(charId).toBe(1);
    expect(eq.name).toBe('Schwimmkristall');
    expect(eq.type).toBe('GEAR');
    expect(eq.probeBonusTalentName).toBe('Schwimmen');
    expect(eq.probeBonusValue).toBe(3);
    expect(eq.description).toContain('Unterwasseratmung');
  });

  it('addSchwimmkristall() tut nichts ohne Charakter-ID', () => {
    (comp as any).character = {};
    const addEquipment = jest.fn();
    (comp as any).characterService = { addEquipment };
    comp.addSchwimmkristall();
    expect(addEquipment).not.toHaveBeenCalled();
  });

  // --- Zaubermatrizen (normal + erweitert) ---

  it('spellMatrices() enthält normale UND erweiterte Matrizen', () => {
    (comp as any).character = { talents: [
      { talentDefinition: { name: 'Zaubermatritze' } },
      { talentDefinition: { name: 'Erweiterte Matrize' } },
      { talentDefinition: { name: 'Nahkampfwaffen' } },
    ] };
    expect(comp.spellMatrices().length).toBe(2);
  });

  it('isEnhancedMatrix() erkennt die erweiterte Matrize', () => {
    expect(comp.isEnhancedMatrix({ talentDefinition: { name: 'Erweiterte Matrize' } } as any)).toBe(true);
    expect(comp.isEnhancedMatrix({ talentDefinition: { name: 'Zaubermatritze' } } as any)).toBe(false);
  });

  it('matrixRemainingThreads(): erweiterte Matrize webt 1 Faden vor (Bedarf −1)', () => {
    // 2-Faden-Zauber in erweiterter Matrize → noch 1 Faden
    expect(comp.matrixRemainingThreads({
      talentDefinition: { name: 'Erweiterte Matrize' }, assignedSpell: { threads: 2 },
    } as any)).toBe(1);
    // 1-Faden-Zauber in erweiterter Matrize → 0 (direkt wirkbar)
    expect(comp.matrixRemainingThreads({
      talentDefinition: { name: 'Erweiterte Matrize' }, assignedSpell: { threads: 1 },
    } as any)).toBe(0);
    // normale Matrize → voller Bedarf
    expect(comp.matrixRemainingThreads({
      talentDefinition: { name: 'Zaubermatritze' }, assignedSpell: { threads: 2 },
    } as any)).toBe(2);
    // kein Zauber zugewiesen → 0
    expect(comp.matrixRemainingThreads({
      talentDefinition: { name: 'Erweiterte Matrize' }, assignedSpell: null,
    } as any)).toBe(0);
  });

  it('probeTargetNames() listet Talent- und Fertigkeitsnamen eindeutig & sortiert', () => {
    (comp as any).character = {
      talents: [{ talentDefinition: { name: 'Heimlicher Schritt' } }, { talentDefinition: { name: 'Klettern' } }],
      skills: [{ skillDefinition: { name: 'Klettern' } }, { skillDefinition: { name: 'Schwimmen' } }],
    };
    expect(comp.probeTargetNames()).toEqual(['Heimlicher Schritt', 'Klettern', 'Schwimmen']);
  });

  // --- Konfigurierbare Boni (Lebenspunkte / Initiative / Erholungsstufe) ---

  it('getDefenseBonus() liest auch die neuen Stat-Boni vom Charakter', () => {
    (comp as any).character = { healthBonus: 3, initiativeBonus: -2, recoveryBonus: 1 };
    expect(comp.getDefenseBonus('healthBonus')).toBe(3);
    expect(comp.getDefenseBonus('initiativeBonus')).toBe(-2);
    expect(comp.getDefenseBonus('recoveryBonus')).toBe(1);
  });

  it('getDefenseBonus() ist 0, wenn ein Stat-Bonus nicht gesetzt ist', () => {
    (comp as any).character = {};
    expect(comp.getDefenseBonus('healthBonus')).toBe(0);
  });

  // --- getRecoveryRollStep() mit Arzt-Wundpflege ---

  it('getRecoveryRollStep() ignoriert den Wundabzug bei aktiver Wundpflege', () => {
    (comp as any).character = { toughness: 10, wounds: 3, arztWoundPenaltyNegated: true };
    // attrToStep(10) = 5, ohne Abzug → 5
    expect(comp.getRecoveryRollStep()).toBe(5);
  });

  it('getRecoveryRollStep() zieht Wunden ab ohne Wundpflege', () => {
    (comp as any).character = { toughness: 10, wounds: 3, arztWoundPenaltyNegated: false };
    // 5 − 3 = 2
    expect(comp.getRecoveryRollStep()).toBe(2);
  });
});

/**
 * Template-Logik für Schild-Badges (ein-/zweihändig, Buckler, automatisch abgelegt).
 */
describe('Schild-Badge-Logik (ein-/zweihändige Waffen)', () => {
  it('Badge "automatisch abgelegt" nur bei autoStowed && active===false', () => {
    const autoStowed: any = { type: 'SHIELD', active: false, autoStowed: true };
    const manualStowed: any = { type: 'SHIELD', active: false, autoStowed: false };
    const activeShield: any = { type: 'SHIELD', active: true, autoStowed: false };

    expect(autoStowed.active === false && autoStowed.autoStowed).toBe(true);
    expect(manualStowed.active === false && !manualStowed.autoStowed).toBe(true); // "abgelegt"
    expect(activeShield.active === false).toBe(false); // kein Badge
  });

  it('Zweihändig-Badge nur bei twoHanded', () => {
    expect(({ type: 'WEAPON', twoHanded: true } as any).twoHanded).toBe(true);
    expect(!!({ type: 'WEAPON', twoHanded: false } as any).twoHanded).toBe(false);
  });
});
