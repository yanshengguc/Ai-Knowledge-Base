package com.yansheng.aiknowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChunkService {
    int saveChunks(Long fileId, List<String> chunks);
}
