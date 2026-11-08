package org.example;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MysqlMemoryStore implements ChatMemoryStore {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private static final Logger log = LoggerFactory.getLogger(MysqlMemoryStore.class);

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = memoryId.toString();
        String json = ChatMessageSerializer.messagesToJson(messages);
        String sql = "INSERT INTO chat_memory (id, messages) VALUES (?, ?) ON DUPLICATE KEY UPDATE messages = ?";
        log.info("写入窗口 [{}]：{} 条消息", id, messages.size());
        jdbcTemplate.update(sql, id, json, json);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sql = "SELECT messages FROM chat_memory WHERE id = ?";

        try {
            String json = jdbcTemplate.queryForObject(sql, String.class, memoryId.toString());
            if (json != null) {
                return ChatMessageDeserializer.messagesFromJson(json);
            }
        } catch (Exception e) {
                return new ArrayList<>();
        }
            return new ArrayList<>();
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sql = "DELETE FROM chat_memory WHERE id = ?";
        jdbcTemplate.update(sql, memoryId.toString());
    }
}
