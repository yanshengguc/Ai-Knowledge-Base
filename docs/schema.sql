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
    nickname VARCHAR(50),
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'admin/user'
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

-- Token 用量记录(8/24):对话与 Embedding 的消耗及成本估算
-- user_id 为 NULL 表示全局/共享消耗(如文档向量化),不归属单个用户
CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    model VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL COMMENT 'chat/embedding',
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    cost_cny DECIMAL(10,6) DEFAULT 0 COMMENT '估算成本(元),按 token-cost 计价配置,非账单',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_type_time (type, create_time)
);

-- ===== B-114 Outline 导航层(10/01 追加,不改写既有表) =====
-- 语义(B 方案):Outline 只做导航层;空父标题不生成空 Chunk;
-- source_chunks 只关联真实正文/后代 Chunk;旧 chunk 与其向量不回填改写。
CREATE TABLE IF NOT EXISTS knowledge_outline_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    parent_id BIGINT NULL COMMENT '父节点 id,根节点为 NULL',
    node_index INT NOT NULL COMMENT '文件内先序序号,从 0 开始',
    level INT NOT NULL COMMENT '标题层级 1-6',
    title VARCHAR(500) NOT NULL,
    heading_path VARCHAR(1000) NOT NULL COMMENT '完整标题路径,以 / 分隔',
    source_start_offset INT NOT NULL COMMENT '标题行起始 UTF-16 下标',
    source_end_offset INT NOT NULL COMMENT '标题行结束 UTF-16 下标(不含换行)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outline_file_id (file_id),
    INDEX idx_outline_parent_id (parent_id),
    CONSTRAINT fk_outline_node_file FOREIGN KEY (file_id) REFERENCES knowledge_file(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS knowledge_outline_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    chunk_index INT NOT NULL COMMENT '冗余:便于溯源排序,免 join knowledge_chunk',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_outline_node_chunk (node_id, chunk_id),
    INDEX idx_outline_ref_chunk_id (chunk_id),
    CONSTRAINT fk_outline_chunk_node FOREIGN KEY (node_id) REFERENCES knowledge_outline_node(id) ON DELETE CASCADE,
    CONSTRAINT fk_outline_chunk_chunk FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk(id) ON DELETE CASCADE
);

-- ===== B-116 user.role 二值权限模型(10/02 追加,不改写既有表) =====
-- 语义:role ∈ {admin,user};与 ADMIN_USERNAMES 白名单叠加做平滑过渡(白名单优先,表内 role 只做增补)。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS;列已存在时会报 1060 Duplicate column name,
-- 属预期报错,忽略即可(本语句幂等重跑安全:重复执行 = 重复报 1060,不影响既有数据)。
ALTER TABLE `user` ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'admin/user' AFTER nickname;