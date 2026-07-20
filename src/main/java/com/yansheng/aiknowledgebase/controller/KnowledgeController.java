package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

@GetMapping("/knowledge")
    public Result<List<KnowledgeVO>> getKnowledgeList(){
        return  Result.success(knowledgeService.getKnowledgeList());
}
@GetMapping("/knowledge/{id}")
    public Result<KnowledgeDetailVO> getKnowledgeDetail(@PathVariable Long id){
        return  Result.success(knowledgeService.getKnowledgeById(id));
}
    @PostMapping("/knowledge")
    public Result<Void> addKnowledge(@RequestBody KnowledgeAddDTO dto){
        System.out.println(dto.getTitle());
        knowledgeService.addKnowledge(dto);
        return Result.success();
    }


}

