package com.yansheng.aiknowledgebase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Eval Harness:工具调用评估集(8/20)
 *
 * 15 个真实用例,统计"工具选择准确率":
 *   ① 该调工具的问题 → 模型是否调了正确工具
 *   ② 不该调工具的问题 → 模型是否忍住没调(成本控制)
 *
 * 面试讲法:我建了 15 题评估集,工具选择准确率 X%,错误案例进缺陷清单迭代
 * (这是"Agent 评估/Evals"考点,也是避坑三查"证据包"的数据来源)
 */
@SpringBootTest
@ActiveProfiles("local")
class EvalHarnessTest {

    @Autowired
    private FunctionCallingService functionCallingService;

    /** (问题, 预期工具: 多个用 | 分隔; 空 = 不该调工具) */
    private static final String[][] CASES = {
            // 该调 file_search(查知识库内容)
            {"有没有讲JVM调优的资料?", "file_search"},
            {"和Redis缓存有关的文档有哪些?", "file_search"},
            {"帮我查一下知识库里关于MySQL索引的内容", "file_search"},
            {"知识库里有没有Spring相关的资料?", "file_search"},
            {"查找与Agent相关的文档", "file_search"},
            // 该调 time_now(实时时间,LLM 不知道)
            {"今天是几号?", "time_now"},
            {"现在几点钟了?", "time_now"},
            // 该调 knowledge_stats(统计概况)
            {"我的知识库里一共有多少资料?", "knowledge_stats"},
            {"统计一下知识库的文件处理情况", "knowledge_stats"},
            // 该调 web_search(明确要求联网)
            {"帮我上网搜一下2026年秋招时间", "web_search"},
            {"上网查一下最新的AI新闻", "web_search"},
            // 不该调工具(寒暄/常识,必须忍住 —— 成本控制)
            {"你好", ""},
            {"1加1等于几?", ""},
            {"什么是RAG?", ""},
            {"用一句话介绍一下你自己", ""},
    };

    @Test
    void evalToolSelectionAccuracy() {
        // 1. 捕获工具调用日志(">>> xxx被调用")
        Logger logger = (Logger) LoggerFactory.getLogger("com.yansheng.aiknowledgebase");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        int total = CASES.length;
        int hit = 0;
        List<String> failures = new ArrayList<>();

        for (String[] c : CASES) {
            String question = c[0];
            String expected = c[1];
            appender.list.clear();

            try {
                functionCallingService.execute(question);
            } catch (Exception e) {
                failures.add(String.format("❌ [%s] 执行异常: %s", question, e.getMessage()));
                continue;
            }

            // 2. 从日志提取实际调用的工具
            List<String> called = new ArrayList<>();
            for (ILoggingEvent evt : appender.list) {
                String msg = evt.getFormattedMessage();
                int idx = msg.indexOf("被调用");
                if (msg.startsWith(">>> ") && idx > 4) {
                    called.add(msg.substring(4, idx).trim());
                }
            }
            String actual = String.join("|", called);

            // 3. 判定:预期空 = 不该调;预期非空 = 必须调且调对
            boolean ok;
            if (expected.isEmpty()) {
                ok = called.isEmpty();
            } else {
                ok = !called.isEmpty() && called.contains(expected);
            }
            if (ok) {
                hit++;
            } else {
                failures.add(String.format("❌ [%s] 预期=%s 实际=%s", question, expected.isEmpty() ? "不调" : expected, actual.isEmpty() ? "未调" : actual));
            }
        }

        logger.detachAppender(appender);

        double accuracy = total == 0 ? 0 : (hit * 100.0 / total);
        System.out.println("\n===== EVAL HARNESS 结果 =====");
        System.out.printf("总用例: %d, 命中: %d, 工具选择准确率: %.1f%%%n", total, hit, accuracy);
        System.out.println("--- 失败案例(进缺陷清单) ---");
        for (String f : failures) {
            System.out.println("  " + f);
        }
        System.out.println("==============================");
        // 断言:准确率 >= 80%(评估集的价值是暴露问题,不是必须 100%)
        org.junit.jupiter.api.Assertions.assertTrue(accuracy >= 80.0,
                "工具选择准确率低于 80%,需排查工具描述/提示词,当前 " + accuracy + "%");
    }
}
