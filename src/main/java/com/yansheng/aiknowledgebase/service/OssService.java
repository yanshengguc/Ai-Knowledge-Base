package com.yansheng.aiknowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {
    String upload(MultipartFile file);

    /** 删除 OSS 对象(传 fileUrl,自动解析 key) */
    void delete(String fileUrl);
}
