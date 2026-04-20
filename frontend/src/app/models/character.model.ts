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
}

export interface CharacterSkill {
  id: number;
  skillDefinition: SkillDefinition;
  rank: number;
}

export type EquipmentType = 'WEAPON' | 'ARMOR' | 'SHIELD';

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
}

export interface CharacterSpell {
  id: number;
  spellDefinition: SpellDefinition;
}

export interface Character {
  id?: number;
  name: string;
  playerName: string;
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

  // Waffe
  weaponName: string;
  weaponDamageStep: number;

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
    physicalDefenseBonus: 0,
    spellDefenseBonus: 0,
    socialDefenseBonus: 0,
    talents: [],
    skills: [],
    equipment: [],
    spells: []
  };
}
