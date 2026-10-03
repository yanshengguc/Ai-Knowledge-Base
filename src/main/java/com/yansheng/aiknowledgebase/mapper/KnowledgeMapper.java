package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.ArrayList;
import java.util.List;

@Mapper
public interface KnowledgeMapper {

    List<KnowledgeEntity> selectAll();
    List<KnowledgeEntity> selectByUserId(Long userId);
    KnowledgeEntity selectById(Long id);

    int insert(KnowledgeEntity knowledgeEntity);
    int update(KnowledgeEntity knowledgeEntity);
    int delete(Long id);

    /** 按用户统计概况(一条 SQL 聚合,消除 N+1):knowledgeCount/fileCount/chunkCount */
    java.util.Map<String, Object> selectStatsByUserId(Long userId);

    /** B-104 服务端分页:按用户 + 关键词(title/content) + 分类过滤计数 */
    long countPageByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                           @Param("category") String category);

    /** B-104 服务端分页:按用户 + 过滤条件取一页(ORDER BY id 保证翻页顺序稳定) */
    List<KnowledgeEntity> selectPageByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                                             @Param("category") String category,
                                             @Param("limit") int limit, @Param("offset") long offset);

    /** B-130 分类下拉候选:当前用户去重后的非空分类(分类为自由文本,故需 distinct 聚合) */
    List<String> selectDistinctCategoriesByUserId(@Param("userId") Long userId);

}
