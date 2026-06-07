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
  | 'THREADWEAVE' | 'SPELL_CAST' | 'TAUNT'
  | 'ACROBATIC_DEFENSE' | 'COMBAT_SENSE'
  | 'DISTRACT' | 'IRON_WILL';

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
  /** Wenn gesetzt: Effekt gilt nur, wenn der Träger gegen diesen Kombattanten agiert. */
  targetCombatantId?: number;
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
  displayName?: string;
  character: Character;
  initiative: number;
  initiativeOrder: number;
  currentInitiativeStep?: number;
  baseInitiativeStep?: number;
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
  pendingRiposteAttackTotal: number;
  tigersprungUsedThisRound: boolean;
  zweitWaffeUsedThisRound: boolean;
  lufttanzActivatedThisRound: boolean;
  lufttanzBonusUsedThisRound: boolean;
  pendingLufttanzTargetId: number;
  pendingLufttanzWeaponId: number;
  blattschussUsedThisRound: boolean;
  pendingBlattschussDefenderId: number;
  pendingBlattschussTotal: number;
  pendingBlattschussKarmaUsed: number;
  pendingBlattschussRank: number;
  pendingBlattschussWeaponId: number;
  pendingBlattschussDefense: number;
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

export interface InitiativeRollDetail {
  combatantId: number;
  combatantName: string;
  npc: boolean;
  step: number;
  roll: RollResult;
  total: number;
  order: number;
  /** Aktive Effekte mit Auswirkung auf die Initiative-Probe (z.B. "Tigersprung +3"). */
  bonusNotes?: string[];
}

export interface LiveModalState {
  version: number;
  /** null = aktuell kein Modal offen; string = Modal-Typ-Diskriminator (z.B. "ATTACK_RESULT"). */
  type: string | null;
  /** Result-DTO (varianten-typisiert je nach `type`). */
  payload?: any;
}

export interface DialogState {
  /** Aktionstyp: "ATTACK", "RANGED_ATTACK", "SPELL", "TAUNT", "DISTRACT" etc. — null = Dialog geschlossen. */
  actionType: string | null;
  targetName?: string;
  weaponName?: string;
  spellName?: string;
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
  /** Letzte Initiative-Probe (Detail-Liste für UI-Modal). */
  lastInitiativeRolls?: InitiativeRollDetail[];
  /** Rundennummer der letzten Initiative-Probe — Frontend triggert Modal bei Änderung. */
  lastInitiativeRollRound?: number;
  /** Synchronisierter Modal-Status für alle Zuschauer (öffnen/schließen via WS). */
  liveModal?: LiveModalState;
  /** Aktive Dialog-Zustände: combatantId → was ein Spieler gerade plant. */
  activeDialogs?: { [combatantId: number]: DialogState };
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
  /** Karma zusätzlich auf den Schadenswurf einsetzen (nur bei Krallenhand-Waffen). */
  spendKarmaForDamage?: boolean;
  /** Blattschuss ankündigen: erlaubt nach Fehlschlag weitere Karma (max. Rang) — nur RANGED_ATTACK. */
  useBlattschuss?: boolean;
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
  /** Rohe STR-Stufe (ohne Wundenabzug — wird separat angezeigt). */
  damageStrengthStep?: number;
  /** Wundenabzug auf den Schaden. */
  damageWoundPenalty?: number;
  /** Waffenbonus auf Schadensstufe. */
  damageWeaponBonus?: number;
  /** Waffenname (zur Anzeige). */
  damageWeaponName?: string;
  damageRoll?: RollResult;
  /** Karma-Würfel auf den Schadenswurf (nur bei Krallenhand). */
  damageKarmaRoll?: RollResult;
  armorValue?: number;
  netDamage?: number;
  woundDealt?: boolean;
  newWounds?: number;
  totalWounds?: number;
  woundThreshold?: number;
  targetDefeated?: boolean;
  attackBonusNotes?: string[];
  /** Notizen zu Schadensboni (z.B. "Schwachstelle erkennen vs X +6 (noch 2 Runden)"). */
  damageBonusNotes?: string[];
  /** Notizen zur Verteidigung des Ziels (z.B. "Defensive Haltung +3"). */
  defenseNotes?: string[];
  hitPendingDodge?: boolean;
  dodgeDefenderId?: number;
  pendingDodgeDamage?: number;
  hitPendingRiposte?: boolean;
  riposteDefenderId?: number;
  /** Lufttanz: Initiative-Vorsprung ≥ 10 → Bonusangriff ausstehend. */
  lufttanzBonusReady?: boolean;
  lufttanzInitiativeDiff?: number;
  /** Blattschuss war aktiviert (kostete 2 Schaden). */
  blattschussActive?: boolean;
  /** Pending: Fehlschlag, weitere Karma einsetzbar. */
  blattschussCanAddKarma?: boolean;
  blattschussKarmaUsed?: number;
  blattschussRank?: number;
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
  damageStepBonus?: number;
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

export interface DistractRequest {
  sessionId: number;
  actorCombatantId: number;
  targetCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
}

export interface DistractResult {
  actorName: string;
  targetName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  socialDefense: number;
  success: boolean;
  successes: number;
  actorPenalty: number;
  targetPenalty: number;
  damageTaken: number;
  description: string;
}

export interface IronWillResult {
  actorName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  attackTotal: number;
  success: boolean;
  effectNegated: boolean;
  damageTaken: number;
  description: string;
}

export interface AcrobaticDefenseResult {
  actorName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  targetNumber: number;
  success: boolean;
  successes: number;
  bonusApplied: number;
  damageTaken: number;
  description: string;
}

export interface CombatSenseRequest {
  sessionId: number;
  actorCombatantId: number;
  targetCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
}

export interface CombatSenseResult {
  actorName: string;
  targetName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  mysticDefense: number;
  success: boolean;
  successes: number;
  defenseBonus: number;
  attackBonus: number;
  damageTaken: number;
  description: string;
}

export interface TauntRequest {
  sessionId: number;
  actorCombatantId: number;
  targetCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
}

export interface TauntResult {
  actorName: string;
  targetName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  socialDefense: number;
  success: boolean;
  extraSuccesses: number;
  penalty: number;
  duration: number;
  resistRoll?: RollResult;
  resistStep: number;
  resisted: boolean;
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

// --- Riposte ---
export interface RiposteRequest {
  sessionId: number;
  defenderCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
  riposteAttempted: boolean;
}
export interface RiposteResult {
  defenderName: string;
  attackerName: string;
  riposteStep: number;
  riposteRoll?: RollResult;
  karmaRoll?: RollResult;
  attackTotal: number;
  riposteAttempted: boolean;
  success: boolean;
  extraSuccesses: number;
  damageCost: number;
  counterAttack: boolean;
  counterAttackTotal: number;
  counterAttackHit: boolean;
  counterDamageStep?: number;
  counterDamageRoll?: RollResult;
  counterArmorValue?: number;
  counterNetDamage?: number;
  counterWoundDealt?: boolean;
  /** Schaden den der Verteidiger erhält wenn Riposte fehlschlägt oder nicht versucht wird. */
  incomingNetDamage?: number;
  description: string;
}

// --- Manövrieren ---
export interface ManoeuverRequest {
  sessionId: number;
  actorCombatantId: number;
  targetCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
}
export interface ManoeuverResult {
  actorName: string;
  targetName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  defenseValue: number;
  success: boolean;
  successes: number;
  defenseBonus: number;
  attackBonus: number;
  damageTaken: number;
  description: string;
}

// --- Lufttanz ---
export interface LufttanzActivationResult {
  actorName: string;
  rank: number;
  initiativeBonus: number;
  damageTaken: number;
  description: string;
}

export interface LufttanzAttackRequest {
  sessionId: number;
  attackerCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
  spendKarmaForDamage?: boolean;
}

// --- Schwachstelle erkennen ---
export interface SpotArmorFlawRequest {
  sessionId: number;
  actorCombatantId: number;
  targetCombatantId: number;
  bonusSteps: number;
  spendKarma: boolean;
}

export interface SpotArmorFlawResult {
  actorName: string;
  targetName: string;
  rollStep: number;
  roll: RollResult;
  karmaRoll?: RollResult;
  /** TN = max(MV, physische Rüstung) */
  targetNumber: number;
  spellDefense: number;
  physicalArmor: number;
  success: boolean;
  successes: number;
  damageBonus: number;
  duration: number;
  strainCost: number;
  description: string;
}

// --- Tigersprung ---
export interface TigersprungResult {
  actorName: string;
  rank: number;
  initiativeBonus: number;
  newInitiative: number;
  damageTaken: number;
  description: string;
}

// --- Zweitwaffe ---
export interface ZweitwaffeRequest {
  sessionId: number;
  actorCombatantId: number;
  defenderCombatantId: number;
  weaponId?: number;
  bonusSteps: number;
  spendKarma: boolean;
}
