package org.example;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;


@SystemMessage("你是一个智能助手。请用纯文本回答，不要使用任何 Markdown 标记。")
interface Assistant {
    String chat(@MemoryId String memoryId, @UserMessage String message);

    TokenStream chatStream(@MemoryId String memoryId, @UserMessage String message);
}


