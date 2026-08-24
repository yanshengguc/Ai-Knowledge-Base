package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Mapper
public interface FileMapper {
void saveFile(FileEntity file);
List<FileEntity> selectFileByKnowledgeId(Long knowledgeId);
int updateStatus(@Param("id") Long id,@Param("status") String status);
    FileEntity selectById(@Param("id") Long id);
    int deleteByKnowledgeId(Long knowledgeId);

    int deleteById(@Param("id") Long id);

    /** 查询某用户拥有的全部文件 id(用于向量检索按用户隔离的 filter) */
    List<Long> selectFileIdsByUserId(Long userId);

    /** 取任意一条文件(测试防御式取数用,不依赖固定 id 的历史数据) */
    @org.apache.ibatis.annotations.Select("SELECT * FROM knowledge_file LIMIT 1")
    FileEntity selectFirstFile();

    /** 按用户统计文件处理状态分布(GROUP BY status) */
    List<java.util.Map<String, Object>> selectStatusSummaryByUserId(Long userId);
}
