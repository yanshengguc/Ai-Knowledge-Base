package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Mapper
public interface FileMapper {
void saveFile(FileEntity file);
List<FileEntity> selectFileByKnowledgeId(Long knowledgeId);

}
