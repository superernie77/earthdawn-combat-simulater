package com.earthdawn.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "discipline_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisciplineDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    /** Karma-Step: Seiten des Karma-Würfels (z.B. 6 = d6) */
    private int karmaStep;

    /** Bewusstlosigkeitsschwellen-Bonus pro Kreis ab Kreis 2 */
    @Column(columnDefinition = "integer default 5")
    @Builder.Default
    private int bwBonusPerCircle = 5;

    /** Todesschwellen-Bonus pro Kreis ab Kreis 2 */
    @Column(columnDefinition = "integer default 6")
    @Builder.Default
    private int tdBonusPerCircle = 6;

    @Column(length = 1000)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discipline_access_talent_names",
            joinColumns = @JoinColumn(name = "discipline_id"))
    @Column(name = "talent_name")
    @Builder.Default
    private List<String> accessTalentNames = new ArrayList<>();
}
