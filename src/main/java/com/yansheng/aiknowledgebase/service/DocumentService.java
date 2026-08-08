package com.yansheng.aiknowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

import javax.swing.text.html.parser.Parser;

public interface DocumentService {
void handleDocument(MultipartFile file,Long fileId);
}
