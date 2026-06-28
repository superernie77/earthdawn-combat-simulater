import { RollResult } from './dice.model';

export type Race =
  | 'ZWERGE' | 'ORKS' | 'MENSCHEN' | 'TROLLE'
  | 'ELFEN' | 'BLUTELFEN' | 'TSKRANG' | 'OBSIDIANER' | 'WINDLINGE';

export const RACES: { value: Race; label: string }[] = [
  { value: 'ZWERGE',     label: 'Zwerge' },
  { value: 'ORKS',       label: 'Orks' },
  { value: 'MENSCHEN',   label: 'Menschen' },
  { value: 'TROLLE',     label: 'Trolle' },
  { value: 'ELFEN',      label: 'Elfen' },
  { value: 'BLUTELFEN',  label: 'Elfen (Blutelfen)' },
  { value: 'TSKRANG',    label: "T'skrang" },
  { value: 'OBSIDIANER', label: 'Obsidianer' },
  { value: 'WINDLINGE',  label: 'Windlinge' },
];

export interface DisciplineDefinition {
  id: number;
  name: string;
  karmaStep: number;
  description: string;
  accessTalentNames: string[];
}

export interface TalentDefinition {
  id: number;
  name: string;
  attribute: string;
  description: string;
  testable: boolean;
  rankScaled: boolean;
  attackTalent: boolean;
  freeAction?: boolean;
  freeActionTestStat?: string;
  freeActionEffectTarget?: 'SELF' | 'TARGET';
  freeActionDamageCost?: number;
  /** Wie oft dieses Talent separat gelernt werden kann (Standard: 1). Zaubermatritze = 3. */
  maxInstances?: number;
  /** Rang wird automatisch aus dem Kreis des Charakters abgeleitet, nicht manuell setzbar. */
  rankFromCircle?: boolean;
}

export interface SkillDefinition {
  id: number;
  name: string;
  attribute: string;
  description: string;
  category: string;
}

export interface CharacterTalent {
  id: number;
  talentDefinition: TalentDefinition;
  rank: number;
  /** Nur für Zaubermatritze: der zugewiesene Zauber (null = leer). */
  assignedSpell?: SpellDefinition | null;
}

export interface CharacterSkill {
  id: number;
  skillDefinition: SkillDefinition;
  rank: number;
}

export type EquipmentType = 'WEAPON' | 'ARMOR' | 'SHIELD' | 'POTION' | 'AMULET' | 'VERBANDSZEUG' | 'GEAR';

export interface Equipment {
  id?: number;
  name: string;
  type: EquipmentType;
  description?: string;
  damageBonus: number;
  physicalArmor: number;
  mysticalArmor: number;
  initiativePenalty: number;
  physicalDefenseBonus: number;
  mysticDefenseBonus: number;
  quantity: number;
  healStep: number;
  /** Krallenhand-Marker: vom Talent verwaltet, kann nicht gelöscht werden, Karma-auf-Schaden möglich. */
  clawWeapon?: boolean;
  /** Heiltrank-Marker: gibt Extra-Erholungsprobe (true) oder verbraucht normale Probe (false). */
  extraRecovery?: boolean;
  /**
   * Nur für ARMOR und SHIELD: Ist das Stück gerade angelegt (aktiv)?
   * Nur das aktive Stück trägt zur Rüstung/Verteidigung/Initiativemalus bei.
   * Default: true (neu hinzugefügte Stücke sind automatisch aktiv).
   */
  active?: boolean;
  // --- Verzweiflungsschlag-Amulett (Typ AMULET) ---
  /** Amulett geladen (einsatzbereit)? Nach Anwendung false, bis per Erholungsprobe aufgeladen. */
  charged?: boolean;
  /** Amulett: true = für Zauber, false = für physische Angriffe. */
  amuletForSpell?: boolean;
  /** Amulett: Stufen-Bonus bei Anwendung (Standard 6). */
  amuletStepBonus?: number;
  /** Blutmagie-Schaden (Amulett: 3) — reduziert dauerhaft Bewusstlosigkeits-/Todesschwelle. */
  bloodMagicDamage?: number;
  /** Waffe: zweihändig zu führen → kein Schild möglich (außer Buckler). */
  twoHanded?: boolean;
  /** Schild: Buckler — auch mit zweihändigen Waffen führbar. */
  buckler?: boolean;
  /** Schild: vom System wegen Zweihandwaffe automatisch abgelegt (wird bei Einhandangriff wieder angelegt). */
  autoStowed?: boolean;
  /** GEAR: Name des Talents/der Fertigkeit, auf die der Gegenstand einen Probenbonus gibt. */
  probeBonusTalentName?: string;
  /** GEAR: Höhe des Probenbonus (z.B. +2 Leichte Stiefel). */
  probeBonusValue?: number;
}

export interface AmuletRechargeResult {
  amuletName: string;
  toughnessStep: number;
  woundPenalty: number;
  rollStep: number;
  roll: RollResult;
  /** true = Wurf ≥ 3, Amulett aufgeladen (Heilung geopfert). */
  recharged: boolean;
  /** Geheilter Schaden, falls Wurf < 3 (sonst 0). */
  healed: number;
  remainingDamage: number;
  recoveryTestsRemaining: number;
  recoveryTestsMax: number;
}

export interface SpellDefinition {
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
  /** Wenn true: im Zauberdialog wird ein Ziel ausgewählt; Schwierigkeit = MV des Ziels. */
  requiresTarget?: boolean;
}

export interface CharacterSpell {
  id: number;
  spellDefinition: SpellDefinition;
}

export interface Character {
  id?: number;
  name: string;
  playerName: string;
  race?: Race;
  circle: number;
  legendPoints: number;
  discipline?: DisciplineDefinition;

  // Attribute
  dexterity: number;
  strength: number;
  toughness: number;
  perception: number;
  willpower: number;
  charisma: number;

  // Abgeleitete Werte (null = auto)
  physicalDefense?: number;
  spellDefense?: number;
  socialDefense?: number;
  woundThreshold?: number;
  unconsciousnessRating?: number;
  deathRating?: number;
  physicalArmor?: number;
  mysticArmor?: number;

  // Konfigurierbarer Bonus auf Verteidigungswerte
  physicalDefenseBonus: number;
  spellDefenseBonus: number;
  socialDefenseBonus: number;

  // Konfigurierbarer Bonus/Malus auf Lebenspunkte (BW & TD), Initiative und Erholungsstufe
  healthBonus?: number;
  initiativeBonus?: number;
  recoveryBonus?: number;

  // Waffe
  weaponName: string;
  weaponDamageStep: number;
  secondaryWeaponId?: number;

  // Karma
  karmaModifier: number;
  karmaMax: number;
  karmaCurrent: number;

  // Währung
  gold: number;
  silver: number;
  copper: number;

  // Zustand
  currentDamage: number;
  wounds: number;
  notes: string;

  // Holzhaut – aktiver Bonus auf Bewusstlosigkeits-/Todesschwelle (0 = nicht aktiv)
  holzhautBonus?: number;

  // Erholungsproben – verbleibende Proben heute (null = voll)
  recoveryTestsRemaining?: number | null;
  // Ausstehender Bonus auf nächste reguläre Erholungsprobe (durch Erholungstrank)
  pendingRecoveryBonus?: number;
  // Nach erfolgreicher Arztbehandlung: nächste Erholungsprobe ohne Wundabzug
  arztWoundPenaltyNegated?: boolean;

  // Spielleiter
  gmCharacter?: boolean;

  talents: CharacterTalent[];
  skills: CharacterSkill[];
  equipment: Equipment[];
  spells: CharacterSpell[];
}

export interface DerivedStats {
  physicalDefense: number;
  spellDefense: number;
  socialDefense: number;
  woundThreshold: number;
  unconsciousnessRating: number;
  deathRating: number;
  initiativeStep: number;
  physicalArmor: number;
  mysticArmor: number;
  karmaStep: number;
  recoveryStep: number;
  carryingCapacity: number;
  /** Aktiver Holzhaut-Bonus (0 = nicht aktiv); bereits in unconsciousnessRating und deathRating eingerechnet. */
  holzhautBonus: number;
  /** Blutmagie-Schaden getragener Amulette (0 = keine); bereits von unconsciousnessRating und deathRating abgezogen. */
  bloodMagicDamage: number;
}

export interface HolzhautResult {
  rank: number;
  toughnessStep: number;
  rollStep: number;
  roll: RollResult | null;
  bonus: number;
  previousBonus: number;
  /** Bei /end: Anzahl der durch Holzhaut geheilten Schadenspunkte. */
  healed: number;
}

export interface RecoveryTestResult {
  toughnessStep: number;
  woundPenalty: number;
  rollStep: number;
  bonusSteps: number;
  roll: RollResult | null;
  /** Karmawürfel (W6/Stufe 4), falls eingesetzt — sonst null. */
  karmaRoll?: RollResult | null;
  healed: number;
  remainingDamage: number;
  recoveryTestsRemaining: number;
  recoveryTestsMax: number;
  usedExtraSlot: boolean;
  potionName: string | null;
}

export interface DrinkPotionResult {
  extraRecovery: boolean;
  potionName: string;
  /** Für Erholungstrank: kumulierter ausstehender Bonus. */
  pendingBonus: number;
  /** Für Heiltrank: Ergebnis der sofortigen Extra-Probe (null bei Erholungstrank). */
  recovery: RecoveryTestResult | null;
}

export interface ArztResult {
  healerName: string;
  woundedName: string;
  wounds: number;
  targetNumber: number;
  perStep: number;
  skillRank: number;
  rollStep: number;
  roll: RollResult;
  success: boolean;
  bonusGranted: number;
  newPendingBonus: number;
  /** true = Wundabzug der nächsten Erholungsprobe wird aufgehoben (bei Erfolg). */
  woundPenaltyNegated: boolean;
  /** Verbleibende Verbandszeug-Anwendungen des Heilers nach dieser Behandlung. */
  verbandszeugRemaining: number;
}

export function emptyCharacter(): Character {
  return {
    name: '',
    playerName: '',
    circle: 1,
    legendPoints: 0,
    dexterity: 10,
    strength: 10,
    toughness: 10,
    perception: 10,
    willpower: 10,
    charisma: 10,
    weaponName: '',
    weaponDamageStep: 5,
    karmaModifier: 5,
    karmaMax: 10,
    karmaCurrent: 10,
    gold: 0,
    silver: 0,
    copper: 0,
    currentDamage: 0,
    wounds: 0,
    notes: '',
    gmCharacter: false,
    physicalDefenseBonus: 0,
    spellDefenseBonus: 0,
    socialDefenseBonus: 0,
    healthBonus: 0,
    initiativeBonus: 0,
    recoveryBonus: 0,
    talents: [],
    skills: [],
    equipment: [],
    spells: []
  };
}
