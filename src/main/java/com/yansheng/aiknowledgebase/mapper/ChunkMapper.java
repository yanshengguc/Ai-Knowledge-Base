package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;

import java.util.List;

public interface ChunkMapper {
    int insertBatch(List<ChunkEntity> chunks);
    List<ChunkEntity> selectByFileId(Long fileId);
}
