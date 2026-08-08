package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;



public interface ChunkService {
    List<ChunkEntity> saveChunks(Long fileId, List<String> chunks);
}