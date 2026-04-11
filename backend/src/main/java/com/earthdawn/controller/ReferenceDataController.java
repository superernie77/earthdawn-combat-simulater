package com.earthdawn.controller;

import com.earthdawn.model.DisciplineDefinition;
import com.earthdawn.model.SkillDefinition;
import com.earthdawn.model.TalentDefinition;
import com.earthdawn.repository.DisciplineRepository;
import com.earthdawn.repository.SkillDefinitionRepository;
import com.earthdawn.repository.TalentDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final DisciplineRepository disciplineRepo;
    private final TalentDefinitionRepository talentRepo;
    private final SkillDefinitionRepository skillRepo;

    @GetMapping("/disciplines")
    public List<DisciplineDefinition> getDisciplines() {
        return disciplineRepo.findAll();
    }

    @GetMapping("/disciplines/{id}")
    public DisciplineDefinition getDiscipline(@PathVariable Long id) {
        return disciplineRepo.findById(id).orElseThrow();
    }

    @GetMapping("/talents")
    public List<TalentDefinition> getTalents() {
        return talentRepo.findAll();
    }

    @GetMapping("/skills")
    public List<SkillDefinition> getSkills() {
        return skillRepo.findAll();
    }
}
