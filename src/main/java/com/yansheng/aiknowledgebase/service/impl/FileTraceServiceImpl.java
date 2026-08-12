package com.yansheng.aiknowledgebase.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.service.FileTraceService;
import com.yansheng.aiknowledgebase.vo.FileVO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;


@Service
public class FileTraceServiceImpl implements FileTraceService {

    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public FileTraceServiceImpl(FileService fileService, ObjectMapper objectMapper) {
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "file_trace";
    }

    @Override
    public String getToolDescription() {
        return "根据文件id查询该文件的来源信息,包括文件名、下载链接和大小。当用户询问某个答案的资料出处、想查看原文档时使用。";
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object rawId = params.get("fileId");
        if (rawId == null) {
            throw new BusinessException("缺少参数fileId");
        }

        Long fileId;
        try {
            fileId = Long.valueOf(rawId.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("参数fileId格式不正确");
        }

        FileVO fileVO = fileService.getFileById(fileId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileVO.getFileName());
        result.put("fileUrl", fileVO.getFileUrl());
        result.put("fileSize", fileVO.getFileSize());
        result.put("updateTime", fileVO.getUpdateTime());

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException("结果序列化失败");
        }
    }
}
