import { Character } from './character.model';
import { RollResult } from './dice.model';

export type CombatStatus = 'SETUP' | 'ACTIVE' | 'FINISHED';
export type CombatPhase = 'DECLARATION' | 'ACTION';
export type DeclaredStance = 'NONE' | 'AGGRESSIVE' | 'DEFENSIVE';
export type DeclaredActionType = 'WEAPON' | 'SPELL';
export type ActionType =
  | 'MELEE_ATTACK' | 'RANGED_ATTACK' | 'SPELL_ATTACK'
  | 'TALENT_TEST' | 'SKILL_TEST' | 'RECOVERY_TEST'
  | 'INITIATIVE' | 'EFFECT_ADDED' | 'EFFECT_REMOVED'
  | 'VALUE_CHANGED' | 'ROUND_CHANGE' | 'FREE_ACTION' | 'DODGE'
  | 'STAND_UP' | 'AUFSPRINGEN'
  | 'THREADWEAVE' | 'SPELL_CAST';

export interface ModifierEntry {
  targetStat: string;
  operation: 'ADD' | 'MULTIPLY' | 'OVERRIDE' | 'SET_MIN' | 'SET_MAX';
  value: number;
  triggerContext: string;
  description?: string;
}

export interface ActiveEffect {
  id?: number;
  name: string;
  description?: string;
  sourceType?: string;
  modifiers: ModifierEntry[];
  remainingRounds: number;
  negative: boolean;
}

export interface KnockdownResult {
  targetName: string;
  rollStep: number;
  roll: RollResult;
  targetNumber: number;
  knockedDown: boolean;
  description: string;
}

export interface StandUpResult {
  actorName: string;
  simpleStandUp: boolean;
  rollStep?: number;
  roll?: RollResult;
  karmaRoll?: RollResult;
  targetNumber?: number;
  success?: boolean;
  damageTaken?: number;
  stillKnockedDown: boolean;
  description: string;
}

export interface CombatantState {
  id: number;
  character: Character;
  initiative: number;
  initiativeOrder: number;
  currentDamage: number;
  wounds: number;
  currentKarma: number;
  defeated: boolean;
  npc: boolean;
  hasActedThisRound: boolean;
  hasDeclared: boolean;
  declaredStance: DeclaredStance;
  declaredActionType: DeclaredActionType;
  knockedDown: boolean;
  pendingAttackBonus: number;
  pendingDefenseBonus: number;
  preparingSpellId?: number;
  threadsWoven: number;
  threadsRequired: number;
  activeEffects: ActiveEffect[];
}

export interface CombatLog {
  id: number;
  round: number;
  loggedAt: string;
  actionType: ActionType;
  actorName?: string;
  targetName?: string;
  description: string;
  success: boolean;
}

export interface CombatSession {
  id: number;
  name: string;
  round: number;
  status: CombatStatus;
  phase: CombatPhase;
  createdAt: string;
  combatants: CombatantState[];
  log: CombatLog[];
}

export interface AttackActionRequest {
  sessionId: number;
  attackerCombatantId: number;
  defenderCombatantId: number;
  actionType: ActionType;
  talentId?: number;
  weaponId?: number;
  bonusSteps: number;
  spendKarma: boolean;
  aggressiveAttack?: boolean;
  defensiveStance?: boolean;
}

export interface CombatActionResult {
  actorName: string;
  targetName: string;
  actionType: ActionType;
  aggressiveAttack: boolean;
  attackStep: number;
  attackRoll: RollResult;
  karmaRoll?: RollResult;
  defenseValue: number;
  hit: boolean;
  extraSuccesses?: number;
  damageStep?: number;
  damageRoll?: RollResult;
  armorValue?: number;
  netDamage?: number;
  woundDealt?: boolean;
  newWounds?: number;
  totalWounds?: number;
  woundThreshold?: number;
  targetDefeated?: boolean;
  attackBonusNotes?: string[];
  hitPendingDodge?: boolean;
  dodgeDefenderId?: number;
  pendingDodgeDamage?: number;
  knockdownResult?: KnockdownResult;
  description: string;
}

export interface DodgeRequest {
  sessionId: number;
  defenderCombatantId: number;
  dodgeAttempted: boolean;
  bonusSteps: number;
  spendKarma: boolean;
}

export interface DodgeResult {
  defenderName: string;
  rollStep: number;
  roll?: RollResult;
  karmaRoll?: RollResult;
  attackTotal: number;
  success: boolean;
  damageCost: number;
  damageStep?: number;
  damageRoll?: RollResult;
  armorValue?: number;
  netDamageApplied: number;
  newWounds: number;
  totalWounds: number;
  woundThreshold: number;
  targetDefeated: boolean;
  knockdownResult?: KnockdownResult;
  description: string;
}

export interface ThreadweaveRequest {
  sessionId: number;
  casterCombatantId: number;
  spellId: number;
  spendKarma: boolean;
}

export interface ThreadweaveResult {
  casterName: string;
  spellName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  targetNumber: number;
  success: boolean;
  threadsWoven: number;
  threadsRequired: number;
  readyToCast: boolean;
  description: string;
}

export interface SpellCastRequest {
  sessionId: number;
  casterCombatantId: number;
  targetCombatantId?: number;
  spellId: number;
  spendKarma: boolean;
}

export interface SpellCastResult {
  casterName: string;
  targetName: string;
  spellName: string;
  effectType: 'DAMAGE' | 'BUFF' | 'DEBUFF' | 'HEAL';
  castStep: number;
  castRoll: RollResult;
  karmaRoll?: RollResult;
  defenseValue: number;
  success: boolean;
  extraSuccesses: number;
  damageStep?: number;
  damageRoll?: RollResult;
  armorValue?: number;
  netDamage?: number;
  woundDealt?: boolean;
  newWounds?: number;
  totalWounds?: number;
  woundThreshold?: number;
  targetDefeated?: boolean;
  knockdownResult?: KnockdownResult;
  effectApplied?: string;
  effectDuration?: number;
  healedAmount?: number;
  description: string;
}

export interface FreeActionRequest {
  sessionId: number;
  actorCombatantId: number;
  targetCombatantId?: number;
  talentId: number;
  bonusSteps: number;
  spendKarma: boolean;
}

export interface FreeActionResult {
  actorName: string;
  targetName?: string;
  talentName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  defenseValue: number;
  success: boolean;
  extraSuccesses: number;
  effectApplied: boolean;
  damageTaken: number;
  description: string;
}
