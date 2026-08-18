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

    /** 按知识查文件列表(含处理状态,详情页展示) */
    @GetMapping("/list/{knowledgeId}")
    public Result getFileList(@PathVariable Long knowledgeId) {
        return Result.success(fileService.listByKnowledgeId(knowledgeId));
    }

    /** 删除文件(级联删切片 + OSS 对象,含权限校验) */
    @DeleteMapping("/{id}")
    public Result deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.success();
    }
}
