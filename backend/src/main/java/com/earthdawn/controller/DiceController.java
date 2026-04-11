package com.earthdawn.controller;

import com.earthdawn.dto.ProbeRequest;
import com.earthdawn.dto.ProbeResult;
import com.earthdawn.dto.RollResult;
import com.earthdawn.service.ProbeService;
import com.earthdawn.service.StepRollService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dice")
@RequiredArgsConstructor
public class DiceController {

    private final StepRollService stepRollService;
    private final ProbeService probeService;

    @PostMapping("/roll")
    public RollResult roll(@RequestBody Map<String, Integer> body) {
        int step = body.getOrDefault("step", 5);
        return stepRollService.roll(step);
    }

    @PostMapping("/probe")
    public ProbeResult probe(@RequestBody ProbeRequest request) {
        return probeService.rollProbe(request);
    }
}
