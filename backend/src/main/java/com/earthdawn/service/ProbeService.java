package com.earthdawn.service;

import com.earthdawn.dto.ProbeRequest;
import com.earthdawn.dto.ProbeResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.AttributeType;
import com.earthdawn.repository.CharacterRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class ProbeService {

    private final StepRollService diceService;
    private final CharacterRepository characterRepo;

    public ProbeResult rollProbe(ProbeRequest req) {
        GameCharacter character = characterRepo.findById(req.getCharacterId())
                .orElseThrow(() -> new EntityNotFoundException("Charakter nicht gefunden: " + req.getCharacterId()));

        int baseStep;
        String probeName;

        if (req.getTalentId() != null) {
            CharacterTalent ct = character.getTalents().stream()
                    .filter(t -> t.getTalentDefinition().getId().equals(req.getTalentId()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Talent nicht auf Charakter: " + req.getTalentId()));

            int attrValue = getAttributeValue(character, ct.getTalentDefinition().getAttribute());
            baseStep = diceService.attributeToStep(attrValue) + ct.getRank() + req.getBonusSteps();
            probeName = ct.getTalentDefinition().getName() + " (Rang " + ct.getRank() + ")";

        } else if (req.getSkillId() != null) {
            CharacterSkill cs = character.getSkills().stream()
                    .filter(s -> s.getSkillDefinition().getId().equals(req.getSkillId()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Fertigkeit nicht auf Charakter: " + req.getSkillId()));

            int attrValue = getAttributeValue(character, cs.getSkillDefinition().getAttribute());
            baseStep = diceService.attributeToStep(attrValue) + cs.getRank() + req.getBonusSteps();
            probeName = cs.getSkillDefinition().getName() + " (Rang " + cs.getRank() + ")";

        } else {
            throw new IllegalArgumentException("talentId oder skillId muss angegeben sein");
        }

        RollResult mainRoll = diceService.roll(baseStep);
        int total = mainRoll.getTotal();

        // Karma
        RollResult karmaRoll = null;
        if (req.isSpendKarma() && character.getKarmaCurrent() > 0) {
            karmaRoll = diceService.roll(6); // Karma ist immer W6
            total += karmaRoll.getTotal();
            character.setKarmaCurrent(Math.max(0, character.getKarmaCurrent() - 1));
            characterRepo.save(character);
        }

        boolean success = total > req.getTargetNumber();
        int extraSuccesses = success ? Math.max(0, (total - req.getTargetNumber()) / 5) : 0;

        return ProbeResult.builder()
                .probeName(probeName)
                .step(baseStep)
                .diceExpression(mainRoll.getDiceExpression())
                .dice(mainRoll.getDice())
                .total(total)
                .targetNumber(req.getTargetNumber())
                .success(success)
                .extraSuccesses(extraSuccesses)
                .successDegree(getSuccessDegree(success, extraSuccesses))
                .karmaUsed(karmaRoll != null)
                .karmaRoll(karmaRoll)
                .build();
    }

    private int getAttributeValue(GameCharacter c, AttributeType attr) {
        // In ED4 FASA: Attributwert = Step-Zahl (1:1)
        return switch (attr) {
            case DEXTERITY  -> c.getDexterity();
            case STRENGTH   -> c.getStrength();
            case TOUGHNESS  -> c.getToughness();
            case PERCEPTION -> c.getPerception();
            case WILLPOWER  -> c.getWillpower();
            case CHARISMA   -> c.getCharisma();
        };
    }

    private String getSuccessDegree(boolean success, int extra) {
        if (!success) return "Fehlschlag";
        return switch (extra) {
            case 0 -> "Erfolg";
            case 1 -> "Guter Erfolg";
            case 2 -> "Ausgezeichneter Erfolg";
            case 3 -> "Herausragender Erfolg";
            default -> "Außergewöhnlicher Erfolg";
        };
    }
}
