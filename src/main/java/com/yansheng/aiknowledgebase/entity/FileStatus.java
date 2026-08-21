package com.yansheng.aiknowledgebase.entity;

/**
 * 文件处理状态(替代散落的字符串魔法值)。
 * 与 knowledge_file.status 列存储值一致。
 */
public enum FileStatus {

    /** 上传成功,正在解析/切片/向量化 */
    PROCESSING,

    /** 全链路处理完成,可检索 */
    SUCCESS,

    /** 处理失败(旧版本会保留,见 FileServiceImpl 覆盖策略) */
    FAILED
}
