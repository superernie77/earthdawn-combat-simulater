package com.earthdawn.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "character_spells")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterSpell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    @JsonIgnore
    private GameCharacter character;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "spell_definition_id")
    private SpellDefinition spellDefinition;
}
