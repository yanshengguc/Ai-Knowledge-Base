package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.service.ChunkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkServiceImpl implements ChunkService {

    private final ChunkMapper chunkMapper;

    public ChunkServiceImpl(ChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Transactional
    @Override
    public List<ChunkEntity> saveChunks(Long fileId, List<String> chunks) {

        if (fileId == null) {
            throw new BusinessException("文件ID不能为空");
        }

        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException("切片内容不能为空");
        }

        List<ChunkEntity> chunkList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int index = 0;

        for (String chunk : chunks) {

            if (chunk == null || chunk.isBlank()) {
                continue;
            }

            ChunkEntity entity = new ChunkEntity();

            entity.setFileId(fileId);
            entity.setChunkIndex(index++);
            entity.setContent(chunk);

            entity.setContentLength(
                    chunk.getBytes(StandardCharsets.UTF_8).length
            );

            entity.setCreateTime(now);
            entity.setUpdateTime(now);

            chunkList.add(entity);
        }

        chunkMapper.insertBatch(chunkList);
        // insertBatch 执行完，chunkList 里每个元素的 id 已经被 MyBatis 回填
        // 这里直接返回同一个 List 引用，调用方能拿到带 id 的实体

        return chunkList;
    }
}