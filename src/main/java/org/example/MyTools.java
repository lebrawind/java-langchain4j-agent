package org.example;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;


@Component
public class MyTools {

    @Tool("计算两数之和，当用户询问加法计算时使用")
    double Sum(double a , double b){
        System.out.println(">>> 工具被调用了！计算两数之和");
        return a + b;
    }
    @Tool("查询指定城市的天气")
    public String getWeather(@P("北京") String city) {
        System.out.println(">>> 工具被调用了！查询天气: " + city);
        // 这里随便返回一个假数据，我们主要是为了测试大模型会不会调用它
        return city + "今天晴天，气温25度。";
    }
}
