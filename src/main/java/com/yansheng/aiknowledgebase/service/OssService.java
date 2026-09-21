package com.yansheng.aiknowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {
    String upload(MultipartFile file);

    /** 删除 OSS 对象(传 fileUrl,自动解析 key) */
    void delete(String fileUrl);

    /**
     * 读取文本对象内容(B-112 在线预览):传 fileUrl 自动解析 key,UTF-8 返回。
     * 仅用于 md 等文本类;超过 MAX_PREVIEW_BYTES 拒绝读取(2C2G 服务器防内存膨胀)。
     */
    String getContent(String fileUrl);
}
