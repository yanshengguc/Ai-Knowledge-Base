package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.List;

@Mapper
public interface KnowledgeMapper {

    List<KnowledgeEntity> selectAll();
    KnowledgeEntity selectById(Long id);

    int insert(KnowledgeEntity knowledgeEntity);
    int update(KnowledgeEntity knowledgeEntity);
    int delete(Long id);

}
