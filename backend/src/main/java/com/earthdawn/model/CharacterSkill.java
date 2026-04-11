package com.earthdawn.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "character_skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "character_id", nullable = false)
    @JsonIgnore
    private GameCharacter character;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_definition_id", nullable = false)
    private SkillDefinition skillDefinition;

    /** Rang 1-10 */
    private int rank;
}
