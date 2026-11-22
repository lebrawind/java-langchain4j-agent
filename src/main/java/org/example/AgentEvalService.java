package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Component
public class AgentEvalService {
    private static final Logger log = LoggerFactory.getLogger(AgentEvalService.class);

    @Autowired
    private MyTools myTools;
    @Autowired
    private RagService ragService;
    @Autowired
    private MysqlMemoryStore mysqlMemoryStore;

    private final ObjectMapper mapper = new ObjectMapper();

    public static final AtomicLong TOKEN_TOTAL = new AtomicLong();

    static class CountingTokenListener implements ChatModelListener{
        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
            TOKEN_TOTAL.addAndGet(responseContext.chatResponse().metadata().tokenUsage().totalTokenCount());
        }
    }
    public record EvalTask(int id,String category, String input, Predicate<String> success, boolean manualReview,String memGroup) { }

    private List<EvalTask> tasks(){
        List<EvalTask> tasks = new ArrayList<>();
        tasks.add(new EvalTask(1,  "天气", "查一下北京的天气",  r -> r.contains("北京") && r.contains("度"), false, "t1"));
        tasks.add(new EvalTask(2,  "天气", "查一下上海的天气",  r -> r.contains("上海") && r.contains("度"), false, "t2"));
        tasks.add(new EvalTask(3,  "天气", "查一下杭州的天气",  r -> r.contains("杭州"), false, "t3"));
        tasks.add(new EvalTask(4,  "天气", "用天气工具查一下广州的天气",  r -> r.contains("广州"), false, "t4"));
        tasks.add(new EvalTask(5,  "加法", "计算 3.7 加 9.2",   r -> r.contains("12.9"), false, "t5"));
        tasks.add(new EvalTask(6,  "加法", "计算 -1.5 加 2.5",   r -> r.contains("1.0"), false, "t6"));
        tasks.add(new EvalTask(7,  "加法", "计算 12345.678 加 8765.322",   r -> r.contains("21111"), false, "t7"));
        tasks.add(new EvalTask(8,  "RAG",  "根据项目笔记，容错三件套是怎么设计的？",  r -> r.contains("重试") && (r.contains("瞬时") || r.contains("预算")), false, "t8"));
        tasks.add(new EvalTask(9,  "RAG",  "滑动窗口的盲区是什么？", r -> r.contains("遗忘"), false, "t9"));
        tasks.add(new EvalTask(10, "RAG",  "AiServices 是怎么装配的？", r -> r.contains("装配") && (r.contains("代理") || r.contains("记忆")), false, "t10"));
        tasks.add(new EvalTask(11, "易错", "计算 0.1 加 0.2",   r -> r.contains("0.3"), false, "t11"));
        tasks.add(new EvalTask(12, "易错", "帮我查一下天气",  r -> !r.isBlank(), true,  "t12"));
        tasks.add(new EvalTask(13, "易错", "帮我查一下亚特兰蒂斯的天气",  r -> !r.isBlank(), true,  "t13"));
        tasks.add(new EvalTask(14, "易错", "量子纠缠是什么？", r -> !r.isBlank(), true,  "t14"));
        tasks.add(new EvalTask(15, "易错", "用天气工具查一下 1+1 等于几", r -> !r.isBlank(), true, "t15"));
        tasks.add(new EvalTask(16, "多轮", "记住：我最喜欢的城市是杭州", r -> !r.isBlank(), false, "fav"));
        tasks.add(new EvalTask(17, "多轮", "我最喜欢的城市是哪个？",     r -> r.contains("杭州"), false, "fav"));
        tasks.add(new EvalTask(18, "稳定性观察", "计算 1 加 1 等于几",   r -> r.contains("2"), false, "t18"));
        tasks.add(new EvalTask(19, "易错", "北京明天的天气是 50 度对吗？", r -> !r.isBlank(), true, "t19"));
        tasks.add(new EvalTask(20, "RAG",  "详细介绍容错设计，为什么这么设计", r -> r.contains("预算") && r.contains("重试") && r.contains("退避"), false, "t20"));
        return tasks;
    }

    private Assistant buildEvalAssistant() {
        ChatModel evalModel = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-chat")
                .listeners(List.of(new CountingTokenListener()))
                .build();
        return AiServices.builder(Assistant.class)
                .chatModel(evalModel)
                .tools(myTools)
                .contentRetriever(ragService.asRetriever())
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id).maxMessages(10).chatMemoryStore(mysqlMemoryStore).build())
                .build();
    }

    public String run(int rounds)throws Exception {
        TOKEN_TOTAL.set(0);
        Assistant evalAssistant = buildEvalAssistant();
        List<EvalTask> tasks = tasks();
        StringBuilder report = new StringBuilder("# Agent 评测报告\n\n| 轮次 | 任务 | 类别 | 判定 | 耗时ms | token | 输出摘要 |\n|---|---|---|---|---|---|---|\n");

        int pass = 0, total = 0;
        int consecutiveFailures = 0;
        long startAll = System.currentTimeMillis();

        for (int round = 1; round <= rounds; round++) {
            for (EvalTask task : tasks) {
                String memId = "eval-r" + round + "-" + task.memGroup();
                long tokensBefore = TOKEN_TOTAL.get();
                long t0 = System.currentTimeMillis();
                String output;
                try {
                    output = evalAssistant.chat(memId, task.input());
                    consecutiveFailures = 0;
                } catch (Exception e) {
                    output = "调用失败：" + e.getMessage();
                    consecutiveFailures++;
                    if (consecutiveFailures >= 3) {
                        log.warn("连续 {} 次调用失败，疑似账户/网络问题，提前终止评测", consecutiveFailures);
                        break;
                    }
                }
                long cost = System.currentTimeMillis() - t0;
                long tokens = TOKEN_TOTAL.get() - tokensBefore;   // 本任务消耗的 token（差值法）
                boolean ok = task.success().test(output);
                if (ok) pass++;
                total++;
                String snippet = output.length() > 40 ? output.substring(0, 40) + "…" : output;
                report.append("| ").append(round).append(" | ").append(task.id()).append(" ")
                        .append(task.category()).append(" | ").append(ok ? "✅" : "❌")
                        .append(" | ").append(cost).append(" | ").append(tokens)
                        .append(" | ").append(snippet.replace("\n", " ")).append(" |\n");
                log.info("[评测] r{} t{} {} 耗时{}ms", round, task.id(), ok ? "通过" : "失败", cost);
            }
        }

        long totalMs = System.currentTimeMillis() - startAll;
        long totalTokens = TOKEN_TOTAL.get();

        report.append("\n## 汇总\n")
                .append("- 任务成功率：").append(pass).append("/").append(total)
                .append("（").append(String.format("%.1f%%", pass * 100.0 / total)).append("）\n")
                .append("- 总耗时：").append(totalMs / 1000).append("s，平均单任务：")
                .append(totalMs / Math.max(total, 1)).append("ms\n")
                .append("- 总 token：").append(totalTokens).append("，平均单任务：")
                .append(totalTokens / Math.max(total, 1)).append("\n");
        log.info("[评测] 完成：{} 轮 × {} 任务", rounds, tasks.size());
        return report.toString();
    }
}
