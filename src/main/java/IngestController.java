package org.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;

@RestController
public class IngestController {

    @Autowired
    RagService ragService;

    @PostMapping("/rag/ingest")
    public String ingestAll() throws IOException {
        File docsDir = new File("docs");
        File[] files = docsDir.listFiles();
        if (files == null) {
            return "docs 目录不存在";
        }
        int total = 0;
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".md") || name.endsWith(".txt")) {
                total += ragService.ingest(name);
            }
        }
        return "共入库 " + total + " 块";
    }

    @GetMapping("/rag/search")
    public String search(@RequestParam String query) {
        return ragService.search(query, 3).toString();
    }
}