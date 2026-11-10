package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.apache.catalina.mapper.Mapper;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


@Component
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int  MAX_CHUNK_SIZE = 500;
    private static final int  HEAD_SPLIT_SIZE = 400;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingModel embeddingModel = new BgeSmallZhV15EmbeddingModel();

    public int ingest(String fileName)throws IOException{
        jdbcTemplate.update("DELETE FROM document_chunk WHERE doc_name = ?",fileName);
        String content = Files.readString(Path.of("docs", fileName));
        List<String>chunks = new ArrayList<>();
        for(String paragraph : content.split("\n\n")){
            String p = paragraph.strip();
            if(p.isEmpty()) continue;
            if(p.length() <= MAX_CHUNK_SIZE) chunks.add(p);
            else {
                for(int i = 0; i < p.length(); i+= HEAD_SPLIT_SIZE) {
                    chunks.add(p.substring(i, Math.min(i + HEAD_SPLIT_SIZE, p.length())));
                }
            }
        }
        List<TextSegment> segments = new ArrayList<>();
        for(String chunk : chunks){
            segments.add(TextSegment.from(chunk));
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        for( int i = 0; i < chunks.size(); i++){
            String vectorJson = objectMapper.writeValueAsString(embeddings.get(i).vector());
            jdbcTemplate.update("INSERT INTO document_chunk (doc_name, content, embedding) VALUES (?, ?, ?)",
                    fileName, chunks.get(i), vectorJson);
        }
        log.info("文档 [{}] 入库完成：{} 块", fileName, chunks.size());
        return chunks.size();
    }
}
