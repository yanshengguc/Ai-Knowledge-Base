package com.yansheng.aiknowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.common.tool.Tool;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * knowledge_stats 工具:统计当前用户知识库概况(知识数/文件数/切片数/处理状态分布)。
 *
 * 与 file_search 构成"条件路由"示例:
 *   用户问"库里有啥/多少资料/统计" → knowledge_stats(概况)
 *   用户问"找某主题的具体内容"     → file_search(内容检索)
 * 设计要点(面试可讲):
 *   1. 无参数工具——模型在"需要概况而非具体内容"时调用,展示多工具条件路由
 *   2. 基于 UserContext 做数据隔离(每个用户只见自己的统计,天然多租户)
 *   3. 状态分布(SUCCESS/FAILED/PROCESSING)让用户感知"资料是否可用"
 */
@Slf4j
@Service
public class KnowledgeStatsTool implements Tool {

    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    public KnowledgeStatsTool(KnowledgeMapper knowledgeMapper,
                              FileMapper fileMapper,
                              ChunkMapper chunkMapper,
                              ObjectMapper objectMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "knowledge_stats";
    }

    @Override
    public String getToolDescription() {
        return "当用户想了解自己知识库的概况/统计信息时使用。例如:『我的知识库里有多少资料?』『我上传了几个文档?』『文件都处理成功了吗?』。"
                + "输入:无参数。"
                + "输出:知识库统计JSON(知识条目数、文件总数、切片总数、文件处理状态分布)。"
                + "注意:① 若用户要找某主题的具体内容,应调用file_search而不是本工具;"
                + "② 若用户要看某个文件详情/处理状态,应调用file_trace。";
    }

    @Override
    public String execute(Map<String, Object> params) {
        log.info(">>> knowledge_stats被调用");

        Long userId = UserContext.getUserId();
        List<KnowledgeEntity> knowledges = knowledgeMapper.selectByUserId(userId);

        int fileCount = 0;
        int chunkCount = 0;
        Map<String, Integer> statusSummary = new LinkedHashMap<>();

        for (KnowledgeEntity k : knowledges) {
            List<FileEntity> files = fileMapper.selectFileByKnowledgeId(k.getId());
            fileCount += files.size();
            for (FileEntity f : files) {
                chunkCount += chunkMapper.selectByFileId(f.getId()).size();
                String status = f.getStatus() == null ? "UNKNOWN" : f.getStatus();
                statusSummary.merge(status, 1, Integer::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeCount", knowledges.size());
        result.put("fileCount", fileCount);
        result.put("chunkCount", chunkCount);
        result.put("statusSummary", statusSummary);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException("结果序列化失败");
        }
    }
}
