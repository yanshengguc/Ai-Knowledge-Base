package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
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
    public int saveChunks(Long fileId, List<String> chunks) {
        List<ChunkEntity> chunkList = new ArrayList<>();
        if (fileId == null) {
            throw new BusinessException("文件ID不能为空");
        }

        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException("切片内容不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        int index=0;
        for (String chunk : chunks) {
            if(chunk == null || chunk.isBlank()) {
                continue;
            }
            ChunkEntity entity = new ChunkEntity();
            entity.setFileId(fileId);
            // 切片顺序
            entity.setChunkIndex(index++);
            // 内容
            entity.setContent(chunk);
            // UTF-8字节长度
            entity.setContentLength(
                    chunk.getBytes(StandardCharsets.UTF_8).length
            );


            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            chunkList.add(entity);

        }
        return chunkMapper.insertBatch(chunkList);

    }

}