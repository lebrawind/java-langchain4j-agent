package org.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistantController {

    @Autowired
    Assistant assistant;

    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam(defaultValue = "default") String memoryId) {
        if (message == null || message.trim().isEmpty()) {
            return "消息不能为空，请输入你想问的内容。";
        }

        return assistant.chat(memoryId, message);
    }
}
