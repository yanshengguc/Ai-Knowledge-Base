package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.service.HelloService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
@Service
public class FileServiceImpl implements FileService {
    private String uploadPath="D:/upload/";
    @Override
    public String  uploadFile(MultipartFile file){
        String originalName= file.getOriginalFilename();
        String uuid = UUID.randomUUID().toString();
        String newName = uuid + "_" + originalName;
        File dir=new File(uploadPath);
        if(!dir.exists()){
            dir.mkdirs();
        }
        try {
            file.transferTo(new File(dir,newName));
        } catch (IOException e){
            throw new RuntimeException("上传失败");
  }
        return uploadPath+newName;
    }
}
