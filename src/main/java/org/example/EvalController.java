package org.example;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class EvalController {

    private final AgentEvalService agentEvalService;
    public EvalController(AgentEvalService agentEvalService) {
        this.agentEvalService = agentEvalService;
    }

    @PostMapping("/eval/run")
    public String run(@RequestParam(defaultValue = "3") int rounds) throws Exception {
        String report = agentEvalService.run(rounds);
        Files.writeString(Path.of("eval-report.md"), report);
        return "评测完成，报告已写入 eval-report.md";
    }
}
