package com.yansheng.aiknowledgebase.controller;

import com.yansheng.aiknowledgebase.common.Result;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
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
    public Result<KnowledgeDetailVO> getKnowledgeDetail(@PathVariable Long id) throws InterruptedException {
        return  Result.success(knowledgeService.getKnowledgeById(id));
}
    @PostMapping("/knowledge")
    public Result<Void> addKnowledge(@RequestBody KnowledgeAddDTO dto){

        knowledgeService.addKnowledge(dto);
        return Result.success();
    }
    @PutMapping("/knowledge/{id}")
    public Result<Void> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeUpdateDTO dto){
        knowledgeService.updateKnowledge(id,dto);
        return Result.success();
    }
@DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id){
        knowledgeService.deleteKnowledge(id);
        return Result.success();
}

    /** 写优先:在知识条目下新建 Markdown 笔记(内容同步切片+向量化,立刻可检索) */
    @PostMapping("/knowledge/{id}/note")
    public Result<Void> createNote(@PathVariable Long id,
                                   @RequestBody com.yansheng.aiknowledgebase.dto.NoteDTO dto) {
        knowledgeService.createNote(id, dto.getTitle(), dto.getContent());
        return Result.success();
    }

}

