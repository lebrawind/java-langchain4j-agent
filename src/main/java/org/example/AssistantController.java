package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class AssistantController {
    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    @Autowired
    Assistant assistant;

    @GetMapping("/chat")
    public String chat(@RequestParam String message, @RequestParam(defaultValue = "default") String memoryId) {
        if (message == null || message.trim().isEmpty()) {
            return "消息不能为空，请输入你想问的内容。";
        }

        return assistant.chat(memoryId, message);
    }

    @GetMapping("/chat/stream")
    public SseEmitter chatStream(@RequestParam String message, @RequestParam(defaultValue = "default") String memoryId) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("消息不能为空，请输入你想问的内容。");
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean done = new AtomicBoolean(false);

        assistant.chatStream(memoryId, message)
                .onPartialResponse(token -> {
                    if (done.get()) return;
                    try {
                        emitter.send(token);
                    } catch (IOException e) {
                        done.set(true);
                        log.warn("SSE 推送失败，客户端可能已断开", e);
                        emitter.complete();
                    }
                })
                .onCompleteResponse(aiMessage -> {
                    if (done.getAndSet(true)) return;
                    emitter.complete();
                })
                .onError(e -> {
                    if (done.getAndSet(true)) return;
                    try {
                        emitter.send("【系统】出错了：" + e.getMessage());
                    } catch (IOException ignored) { }
                    emitter.completeWithError(e);
                })
                .start();
        return emitter;
    }
}
