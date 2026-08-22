package com.yansheng.aiknowledgebase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.service.FunctionCallingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eval Harness:工具调用评估集(数据集驱动,借鉴 DeepSeek Harness 工程模式)
 *
 * 用例在 src/test/resources/eval/cases.json —— 加用例只改 JSON,不动代码。
 * 指标分层统计:总体准确率 + 按任务类型(retrieval/time/stats/web/refusal)准确率 + 失败明细。
 *
 * 面试讲法:① 数据集驱动,评估集可扩展可共享;
 *           ② 指标分层,一眼看到短板在哪个任务类型;
 *           ③ 失败案例进缺陷清单,修复后重跑验证(评估与开发闭环)。
 */
@SpringBootTest
@Tag("e2e")
@ActiveProfiles("local")
class EvalHarnessTest {

    /** 数据集文件路径(可扩展:加用例只改这里) */
    private static final String CASES_FILE = "eval/cases.json";

    @Autowired
    private FunctionCallingService functionCallingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void evalToolSelectionAccuracy() throws Exception {
        // 1. 读取数据集
        EvalDataset dataset;
        try (InputStream in = new ClassPathResource(CASES_FILE).getInputStream()) {
            dataset = objectMapper.readValue(in, EvalDataset.class);
        }
        List<EvalCase> cases = dataset.cases;
        Map<String, String> meta = dataset.meta;

        // 2. 捕获工具调用日志(">>> xxx被调用")
        Logger logger = (Logger) LoggerFactory.getLogger("com.yansheng.aiknowledgebase");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        int total = cases.size();
        int hit = 0;
        // 分层统计:task -> [命中, 总数]
        Map<String, int[]> byTask = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        for (EvalCase c : cases) {
            appender.list.clear();
            byTask.putIfAbsent(c.task, new int[2]);
            byTask.get(c.task)[1]++;

            try {
                functionCallingService.execute(c.question);
            } catch (Exception e) {
                failures.add(String.format("❌ [%s] %s 执行异常: %s", c.id, c.question, e.getMessage()));
                continue;
            }

            // 3. 从日志提取实际调用的工具
            List<String> called = new ArrayList<>();
            for (ILoggingEvent evt : appender.list) {
                String msg = evt.getFormattedMessage();
                int idx = msg.indexOf("被调用");
                if (msg.startsWith(">>> ") && idx > 4) {
                    called.add(msg.substring(4, idx).trim());
                }
            }
            String actual = String.join("|", called);

            // 4. 判定:预期空 = 不该调;预期非空 = 必须调且调对
            boolean ok;
            if (c.expectTools.isEmpty()) {
                ok = called.isEmpty();
            } else {
                ok = called.stream().anyMatch(c.expectTools::contains);
            }
            if (ok) {
                hit++;
                byTask.get(c.task)[0]++;
            } else {
                failures.add(String.format("❌ [%s/%s] 预期=%s 实际=%s",
                        c.id, c.task,
                        c.expectTools.isEmpty() ? "不调" : String.join("|", c.expectTools),
                        actual.isEmpty() ? "未调" : actual));
            }
        }

        logger.detachAppender(appender);

        // 5. 输出分层报告
        double accuracy = total == 0 ? 0 : (hit * 100.0 / total);
        System.out.println("\n===== EVAL HARNESS 结果 =====");
        System.out.printf("数据集: %s v%s | 模型: %s | 温度: %s | 用例数: %d%n",
                meta.getOrDefault("name", "-"), meta.getOrDefault("version", "-"),
                meta.getOrDefault("model", "-"), meta.getOrDefault("temperature", "-"), total);
        System.out.printf("总体工具选择准确率: %d/%d = %.1f%%%n", hit, total, accuracy);
        System.out.println("--- 按任务类型 ---");
        for (Map.Entry<String, int[]> e : byTask.entrySet()) {
            int[] v = e.getValue();
            System.out.printf("  %-10s %d/%d = %.1f%%%n", e.getKey(), v[0], v[1], v[0] * 100.0 / v[1]);
        }
        System.out.println("--- 失败案例(进缺陷清单,修复后重跑验证) ---");
        for (String f : failures) {
            System.out.println("  " + f);
        }
        System.out.println("==============================");

        // 6. 断言:准确率 >= 80%(评估集的价值是暴露问题,不是必须 100%)
        org.junit.jupiter.api.Assertions.assertTrue(accuracy >= 80.0,
                "工具选择准确率低于 80%,需排查工具描述/提示词,当前 " + accuracy + "%");
    }

    /** 数据集结构(与 cases.json 对应) */
    static class EvalDataset {
        public Map<String, String> meta = new LinkedHashMap<>();
        public List<EvalCase> cases = new ArrayList<>();
    }

    static class EvalCase {
        public String id;
        public String task;
        public String difficulty;
        public String question;
        public List<String> expectTools = new ArrayList<>();
    }
}
