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
}
