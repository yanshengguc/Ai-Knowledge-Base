package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.service.FileService;
import com.yansheng.aiknowledgebase.vo.FilePageVO;
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

    /** B-104 服务端分页:按知识分页返回文件列表(默认 10 / 上限 50;旧接口保持全量不变) */
    @GetMapping("/page/{knowledgeId}")
    public Result<FilePageVO> getFilePage(@PathVariable Long knowledgeId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return Result.success(fileService.getFilePage(knowledgeId, page, size));
    }

    /** B-112 在线预览:返回文件原文(md 文本类);pdf/docx 返回元信息由前端提示不支持 */
    @GetMapping("/{id}/content")
    public Result getFileContent(@PathVariable Long id) {
        return Result.success(fileService.getFileContent(id));
    }

    /** 删除文件(级联删切片 + OSS 对象,含权限校验) */
    @DeleteMapping("/{id}")
    public Result deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.success();
    }
}
