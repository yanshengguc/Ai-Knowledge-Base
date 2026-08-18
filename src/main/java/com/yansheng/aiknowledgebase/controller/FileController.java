package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/file")
public class FileController {
    @Autowired
    private FileService fileService;

    @PostMapping("/upload/{knowledgeId}")
    public Result upload(@RequestParam("file") MultipartFile file,@PathVariable Long knowledgeId){

        FileEntity fileEntity=  fileService.uploadFile(file,knowledgeId);
        return Result.success(fileEntity);
    }

    /** 查询文件处理状态(前端上传后轮询:PROCESSING -> SUCCESS) */
    @GetMapping("/{id}")
    public Result getFileById(@PathVariable Long id) {
        return Result.success(fileService.getFileById(id));
    }
}
