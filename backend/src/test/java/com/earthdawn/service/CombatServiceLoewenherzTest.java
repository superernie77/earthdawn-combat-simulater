package com.earthdawn.service;

import com.earthdawn.dto.LoewenherzResult;
import com.earthdawn.model.*;
import com.earthdawn.model.enums.*;
import com.earthdawn.repository.CharacterRepository;
import com.earthdawn.repository.CombatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * Löwenherz: freie Aktion, 1 Überanstrengung. Die Löwenherzstufe (WIL + Rang) tritt bei
 * Willenskraftproben zum Abschütteln an die Stelle der normalen WIL-Stufe.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceLoewenherzTest {

    @Mock CombatSessionRepository sessionRepo;
    @Mock CharacterRepository characterRepo;
    @Mock StepRollService diceService;
    @Mock ModifierAggregator modifiers;
    @Mock SimpMessagingTemplate websocket;
    @Mock ObjectMapper objectMapper;

    @InjectMocks CombatService combatService;

    private CombatSession session;
    private CombatantState actor;

    @BeforeEach
    void setUp() {
        actor = combatant(10L, "Sarin", 4);
        session = CombatSession.builder()
                .id(1L).round(1)
                .phase(CombatPhase.ACTION)
                .status(CombatStatus.ACTIVE)
                .combatants(new ArrayList<>(List.of(actor)))
                .log(new ArrayList<>())
                .build();

        lenient().when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(session));
        lenient().when(sessionRepo.save(any(CombatSession.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(diceService.attributeToStep(anyInt())).thenAnswer(i -> {
            int v = i.getArgument(0);
            return v <= 3 ? 2 : v <= 6 ? 3 : v <= 9 ? 4 : v <= 12 ? 5 : v <= 15 ? 6 : 7;
        });
    }

    @Test
    void aktivierung_kostet1Ueberanstrengung_undSetztEffekt() {
        LoewenherzResult r = combatService.performLoewenherz(1L, 10L);

        assertThat(r.getRank()).isEqualTo(4);
        assertThat(r.getResistBonus()).isEqualTo(4);
        assertThat(r.getDamageTaken()).isEqualTo(1);
        assertThat(actor.getCurrentDamage()).isEqualTo(1);

        ActiveEffect effect = actor.getActiveEffects().stream()
                .filter(e -> TalentNames.LOEWENHERZ.equals(e.getName()))
                .findFirst().orElseThrow();
        ModifierEntry mod = effect.getModifiers().get(0);
        assertThat(mod.getTargetStat()).isEqualTo(StatType.WILLPOWER_RESIST_STEP);
        assertThat(mod.getOperation()).isEqualTo(ModifierOperation.ADD);
        assertThat(mod.getValue()).isEqualTo(4);
        assertThat(effect.getRemainingRounds()).isEqualTo(1);
    }

    @Test
    void aktivierung_verbrauchtKeineHauptaktion() {
        combatService.performLoewenherz(1L, 10L);
        assertThat(actor.isHasActedThisRound()).isFalse();
    }

    @Test
    void erneutesWirken_ersetztStattZuStapeln() {
        combatService.performLoewenherz(1L, 10L);
        combatService.performLoewenherz(1L, 10L);

        long count = actor.getActiveEffects().stream()
                .filter(e -> TalentNames.LOEWENHERZ.equals(e.getName())).count();
        assertThat(count).isEqualTo(1);
        assertThat(combatService.loewenherzBonus(actor)).isEqualTo(4);
        assertThat(actor.getCurrentDamage()).isEqualTo(2);   // beide Male 1 Überanstrengung
    }

    @Test
    void ohneTalent_schlaegtFehl() {
        actor.getCharacter().getTalents().clear();
        assertThatThrownBy(() -> combatService.performLoewenherz(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Löwenherz");
    }

    @Test
    void besiegt_schlaegtFehl() {
        actor.setDefeated(true);
        assertThatThrownBy(() -> combatService.performLoewenherz(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("besiegt");
    }

    @Test
    void bonus_istNullOhneAktivierung() {
        assertThat(combatService.loewenherzBonus(actor)).isZero();
    }

    @Test
    void bonus_entsprichtDemRang() {
        combatService.performLoewenherz(1L, 10L);
        assertThat(combatService.loewenherzBonus(actor)).isEqualTo(4);
    }

    private CombatantState combatant(Long id, String name, int rank) {
        List<CharacterTalent> talents = new ArrayList<>();
        if (rank > 0) {
            talents.add(CharacterTalent.builder()
                    .id(id).rank(rank)
                    .talentDefinition(TalentDefinition.builder()
                            .id(id).name(TalentNames.LOEWENHERZ)
                            .attribute(AttributeType.WILLPOWER).build())
                    .build());
        }
        GameCharacter c = GameCharacter.builder()
                .id(id).name(name).willpower(14)
                .talents(talents)
                .equipment(new ArrayList<>())
                .skills(new ArrayList<>())
                .spells(new ArrayList<>())
                .build();
        return CombatantState.builder()
                .id(id).character(c)
                .activeEffects(new ArrayList<>())
                .build();
    }
}
