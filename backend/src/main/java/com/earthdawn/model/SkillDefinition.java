package com.earthdawn.model;

import com.earthdawn.model.enums.AttributeType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "skill_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private AttributeType attribute;

    @Column(length = 1000)
    private String description;

    private String category;
}
