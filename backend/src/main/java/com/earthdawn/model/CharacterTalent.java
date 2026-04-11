package com.earthdawn.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "character_talents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterTalent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "character_id", nullable = false)
    @JsonIgnore
    private GameCharacter character;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "talent_definition_id", nullable = false)
    private TalentDefinition talentDefinition;

    /** Rang 1-15 */
    private int rank;
}
