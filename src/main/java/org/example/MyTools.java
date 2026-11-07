package org.example;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;


@Component
public class MyTools {
    private static final Logger log = LoggerFactory.getLogger(MyTools.class);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BUDGET_MS = 15000;
    private static final long INITIAL_BACKOFF_MS = 1000;


    @Tool("计算两数之和，当用户询问加法计算时使用")
    double Sum(double a , double b){
        log.info(">>> 工具被调用了！计算两数之和");
        return a + b;
    }
    @Tool("查询指定城市的天气")
    public String getWeather(@P("要查询天气的城市名称") String city) {
        long start = System.currentTimeMillis();
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("城市名称不能为空");
        }
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info(">>> 工具被调用了！查询天气: {}", city);
                return city + "晴，25度，微风";
            } catch (Exception e) {
                if (!(e instanceof TimeoutException)) {
                    // 非瞬时错误（参数/业务/未知）：不重试，包装后交回模型
                    throw new RuntimeException("天气查询失败：" + e.getMessage());
                }
                boolean lastAttempt = attempt == MAX_RETRIES;
                boolean overBudget = System.currentTimeMillis() - start > RETRY_BUDGET_MS;
                long backoff = INITIAL_BACKOFF_MS * (attempt + 1);
                log.info("第 {} 次尝试失败：{}", attempt + 1, e.getMessage());
                if(lastAttempt || overBudget){
                    throw new RuntimeException("天气服务暂时不可用（已尝试 " + (attempt + 1) + " 次）：" + e.getMessage());
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();   // 恢复中断标志
                    throw new RuntimeException("重试被中断", ie);
                }
                log.info("等待 {}ms 后重试", backoff);
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
