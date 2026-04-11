export interface DieRollDetail {
  sides: number;
  rolls: number[];
  total: number;
  exploded: boolean;
}

export interface RollResult {
  step: number;
  diceExpression: string;
  dice: DieRollDetail[];
  total: number;
  exploded: boolean;
}

export interface ProbeRequest {
  characterId: number;
  talentId?: number;
  skillId?: number;
  bonusSteps: number;
  targetNumber: number;
  spendKarma: boolean;
}

export interface ProbeResult {
  probeName: string;
  step: number;
  diceExpression: string;
  dice: DieRollDetail[];
  total: number;
  targetNumber: number;
  success: boolean;
  extraSuccesses: number;
  successDegree: string;
  karmaUsed: boolean;
  karmaRoll?: RollResult;
}
