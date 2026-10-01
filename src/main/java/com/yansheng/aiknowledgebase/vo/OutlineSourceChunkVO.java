package com.yansheng.aiknowledgebase.vo;

import lombok.Data;

/** source_chunks 溯源条目:指向真实 knowledge_chunk,preview 由服务端截断 */
@Data
public class OutlineSourceChunkVO {
    private Long chunkId;
    private Integer chunkIndex;
    private Integer contentLength;
    private String preview;
}