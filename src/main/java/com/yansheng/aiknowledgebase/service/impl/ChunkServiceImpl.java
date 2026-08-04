package com.yansheng.aiknowledgebase.service.impl;

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
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            ChunkEntity entity = new ChunkEntity();
            entity.setFileId(fileId);
            // 切片顺序
            entity.setChunkIndex(i);
            // 内容
            entity.setContent(chunk);
            // UTF-8字节长度
            entity.setContentLength(
                    chunk.getBytes(StandardCharsets.UTF_8).length
            );

            LocalDateTime now = LocalDateTime.now();
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            chunkList.add(entity);

        }
        return chunkMapper.insertBatch(chunkList);

    }

}