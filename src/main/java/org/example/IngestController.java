package org.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class IngestController {

    private final RagService ragService;

    public IngestController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/rag/ingest")
    public String ingestAll() throws IOException {
        File docsDir = new File("docs");
        File[] files = docsDir.listFiles();
        if (files == null) {
            return "docs 目录不存在";
        }
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (!name.endsWith(".md") && !name.endsWith(".txt")) {
                continue;
            }
            try {
                ok += ragService.ingest(name);
            } catch (Exception e) {
                failed.add(name + "：" + e.getMessage());
            }
        }
        return "成功入库 " + ok + " 块" + (failed.isEmpty() ? "" : "；失败：" + failed);
    }

    @GetMapping("/rag/search")
    public List<String> search(@RequestParam String query) {
        return ragService.search(query, 3);
    }
}