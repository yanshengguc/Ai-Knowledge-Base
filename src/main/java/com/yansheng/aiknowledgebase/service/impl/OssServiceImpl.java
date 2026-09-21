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
        String key = parseKey(fileUrl);
        if (key == null) {
            return;
        }
        try {
            ossClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            // OSS 删除失败不影响主流程(对象残留可接受,记录日志)
            log.warn("OSS 删除失败,key={}", key, e);
        }
    }

    @Override
    public String getContent(String fileUrl) {
        String key = parseKey(fileUrl);
        if (key == null || key.isBlank()) {
            throw new com.yansheng.aiknowledgebase.exception.BusinessException("文件地址无效");
        }
        try (com.aliyun.oss.model.OSSObject obj = ossClient.getObject(bucketName, key)) {
            long len = obj.getObjectMetadata() != null
                    && obj.getObjectMetadata().getContentLength() >= 0
                            ? obj.getObjectMetadata().getContentLength() : -1;
            if (len > MAX_PREVIEW_BYTES) {
                throw new com.yansheng.aiknowledgebase.exception.BusinessException(
                        "文件过大,不支持在线预览(上限 5MB)");
            }
            // 大小兜底:逐字节限量读,即使 ContentLength 缺失也不会无界吃内存
            java.io.InputStream in = obj.getObjectContent();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > MAX_PREVIEW_BYTES) {
                    throw new com.yansheng.aiknowledgebase.exception.BusinessException(
                            "文件过大,不支持在线预览(上限 5MB)");
                }
                buf.write(chunk, 0, n);
            }
            return buf.toString("UTF-8");
        } catch (com.yansheng.aiknowledgebase.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSS 读取失败,key={}", key, e);
            throw new com.yansheng.aiknowledgebase.exception.BusinessException("原文读取失败");
        }
    }

    /** 上限 5MB:文本预览足够(整本 md 教材通常 <1MB),防大文件拖垮 2C2G 服务器 */
    static final long MAX_PREVIEW_BYTES = 5L * 1024 * 1024;

    /** 从 fileUrl 解析对象 key(与 delete 共用口径):去掉 https://{bucket}.{endpoint}/ 前缀与查询串 */
    private String parseKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String prefix = "https://" + bucketName + "." + endpoint + "/";
        String key = fileUrl.startsWith(prefix) ? fileUrl.substring(prefix.length()) : fileUrl;
        if (key.contains("?")) {
            key = key.substring(0, key.indexOf("?"));
        }
        return key;
    }
}
