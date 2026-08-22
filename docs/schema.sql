-- Ai-Knowledge-Base 建表脚本（MySQL 8.x，utf8mb4）
-- 执行方式：mysql -u root -p < docs/schema.sql

CREATE DATABASE IF NOT EXISTS ai_knowledge_base
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_knowledge_base;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50)
);

-- 知识条目表
CREATE TABLE IF NOT EXISTS knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200),
    content TEXT,
    category VARCHAR(50),
    author VARCHAR(50),
    user_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 文件表（OSS 文件元信息，与知识条目关联）
CREATE TABLE IF NOT EXISTS knowledge_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(255),
    file_size BIGINT,
    file_url VARCHAR(500),
    knowledge_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCESS/FAILED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge_id (knowledge_id),
    FOREIGN KEY (knowledge_id) REFERENCES knowledge(id) ON DELETE CASCADE
);

-- 文档切片表（RAG 切片，后续做 Embedding 与向量检索）
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT,
    content_length INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_file_id (file_id),
    FOREIGN KEY (file_id) REFERENCES knowledge_file(id) ON DELETE CASCADE
);

-- 混合检索 BM25 路(8/22):ngram 中文全文索引,补向量检索对精确匹配/专有名词的短板
-- 已存在索引时执行会报错,可先 DROP INDEX ft_content ON knowledge_chunk; 再执行
CREATE FULLTEXT INDEX ft_content ON knowledge_chunk(content) WITH PARSER ngram;
