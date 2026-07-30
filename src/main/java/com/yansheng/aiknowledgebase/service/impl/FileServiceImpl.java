package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.service.HelloService;
import com.yansheng.aiknowledgebase.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
@Service
public class FileServiceImpl implements FileService {
   @Autowired
   private OssService ossService;
    @Override
    public String  uploadFile(MultipartFile file){


           String url=ossService.upload(file);

        return url;
    }
}
