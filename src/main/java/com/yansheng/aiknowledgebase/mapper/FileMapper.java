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
}
