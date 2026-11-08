package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;


@Component
public class MyTools {
    @Value("${amap.key}")
    private String amapKey;
    private static final Logger log = LoggerFactory.getLogger(MyTools.class);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BUDGET_MS = 15000;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> adcodeCache = new ConcurrentHashMap<>();
    private final RestClient restClient = buildRestClient();

    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).build();
    }


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
                String adcode = getAdcode(city);
                String weatherJson = restClient.get()
                        .uri("https://restapi.amap.com/v3/weather/weatherInfo?city={adcode}&key={key}", adcode, amapKey)
                        .retrieve()
                        .body(String.class);
                JsonNode weatherRoot = mapper.readTree(weatherJson);
                if (!"1".equals(weatherRoot.path("status").asText())) {
                    throw new IllegalStateException("高德天气接口返回错误：" + weatherRoot.path("info").asText());
                }
                JsonNode live = weatherRoot.path("lives").path(0);
                if (live.isMissingNode()) {
                    throw new IllegalStateException("该城市暂无天气实况数据");
                }
                return city + "今天" + live.path("weather").asText("未知")
                        + "，气温 " + live.path("temperature").asText("未知") + " 度，"
                        + live.path("winddirection").asText("") + "风 " + live.path("windpower").asText("") + " 级，"
                        + "湿度 " + live.path("humidity").asText("") + "%";
            } catch (Exception e) {
                boolean transientError = e instanceof TimeoutException
                        || e instanceof org.springframework.web.client.ResourceAccessException;
                if (!transientError) {
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

    private String getAdcode(String city) {
        String cached = adcodeCache.get(city);
        if (cached != null) {
            return cached;
        }
        String body = restClient.get()
                .uri("https://restapi.amap.com/v3/geocode/geo?address={city}&key={key}", city, amapKey)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = mapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                throw new IllegalStateException("高德接口返回错误：" + root.path("info").asText()
                        + "（infocode: " + root.path("infocode").asText() + "）");
            }
            if (root.path("count").asInt(0) == 0) {
                throw new IllegalStateException("查不到该城市：" + city);
            }
            String adcode = root.path("geocodes").path(0).path("adcode").asText();
            adcodeCache.put(city, adcode);
            return adcode;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("地理编码解析失败：" + e.getMessage(), e);
        }

    }


}
