package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.SearchResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChunkMapper {
    int insertBatch(List<ChunkEntity> chunks);
    List<ChunkEntity> selectByFileId(Long fileId);
    int deleteByFileId(Long fileId);

    /**
     * BM25 全文检索(混合检索的第二路,补向量检索对精确匹配/专有名词的短板)。
     * MySQL FULLTEXT + ngram 中文分词,按用户文件范围过滤。
     * score = MATCH 相关度(越大越相关,与向量距离的语义相反,只用于排序不用于阈值过滤)。
     */
    List<SearchResult> selectByFullText(@Param("userId") Long userId,
                                        @Param("query") String query,
                                        @Param("limit") int limit);
}
