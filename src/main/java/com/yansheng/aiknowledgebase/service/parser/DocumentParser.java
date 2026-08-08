package com.yansheng.aiknowledgebase.service.parser;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentParser {
    String parse(MultipartFile file);
}
