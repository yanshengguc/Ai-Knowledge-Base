package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.TokenUsageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TokenUsageMapper {

    int insert(TokenUsageEntity entity);

    /** 用户对话用量汇总:按模型 + 今日/本月/累计条件聚合 */
    Map<String, Object> selectChatSummaryByUserId(Long userId);

    /** 全局 embedding 用量汇总(向量化/记忆检索共享,不归属单用户) */
    Map<String, Object> selectEmbeddingSummary();

    /** 近 N 天逐日用量(对话按用户,含全局 embedding 合并趋势) */
    List<Map<String, Object>> selectDailyTrend(@Param("userId") Long userId, @Param("days") int days);
}
