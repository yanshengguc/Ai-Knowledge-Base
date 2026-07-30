package com.yansheng.aiknowledgebase.service.impl;

import com.aliyun.oss.OSS;
import com.yansheng.aiknowledgebase.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class OssServiceImpl implements OssService {
    @Autowired
    private OSS ossClient;
    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Override
    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName = UUID.randomUUID().toString()+"_"+originalFilename;
  try {
      ossClient.putObject(
              bucketName,
              fileName,
              file.getInputStream()
      );
  }catch (IOException e){
            throw new RuntimeException("上传失败");
  }
        String url = "https://"
                + bucketName
                + "."
                + endpoint
                + "/"
                + fileName;
        return url;

    }
}
