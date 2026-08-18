package com.yansheng.aiknowledgebase.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.aliyun.oss.OSS;
import com.yansheng.aiknowledgebase.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
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

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        String prefix = "https://" + bucketName + "." + endpoint + "/";
        String key = fileUrl.startsWith(prefix) ? fileUrl.substring(prefix.length()) : fileUrl;
        if (key.contains("?")) {
            key = key.substring(0, key.indexOf("?"));
        }
        try {
            ossClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            // OSS 删除失败不影响主流程(对象残留可接受,记录日志)
            log.warn("OSS 删除失败,key={}", key, e);
        }
    }
}
