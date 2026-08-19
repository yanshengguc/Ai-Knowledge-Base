package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * time_now 工具:返回当前本地时间(含星期)。
 *
 * 设计要点(面试可讲):
 *   1. LLM 训练数据有截止时间,不知道"现在"——问日期/时间必须调工具,
 *      这是"模型何时该用工具"的经典案例(工具调用时机判断)
 *   2. 无参数工具,模型仅通过描述判断调用时机
 */
@Slf4j
@Service
public class TimeNowTool implements Tool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public TimeNowTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "time_now";
    }

    @Override
    public String getToolDescription() {
        return "当用户询问当前时间、日期、星期,或需要基于当前时间做判断时使用(模型不知道实时时间,必须通过本工具获取)。"
                + "例如:『今天几号?』『现在几点?』『今天是星期几?』『我上次上传是什么时候?』(配合file_trace时)。"
                + "输入:无参数。"
                + "输出:当前本地时间(含星期)。"
                + "注意:与时间无关的问题直接回答,不要调用本工具。";
    }

    @Override
    public String execute(Map<String, Object> params) {
        log.info(">>> time_now被调用");

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        DayOfWeek dayOfWeek = DayOfWeek.from(now);
        String weekday = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.SIMPLIFIED_CHINESE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("localTime", now.format(FORMATTER));
        result.put("weekday", weekday);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException("结果序列化失败");
        }
    }
}
