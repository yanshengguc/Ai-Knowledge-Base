package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;

import java.util.List;

public interface KnowledgeService {
    List<KnowledgeVO> getKnowledgeList();

 KnowledgeDetailVO getKnowledgeById(Long id) throws InterruptedException;
  void addKnowledge(KnowledgeAddDTO dto);
  void  updateKnowledge(Long id, KnowledgeUpdateDTO dto);
  void deleteKnowledge(Long id);

    /**
     * 写优先:在知识条目下新建 Markdown 笔记。
     * 笔记 = 特殊文件(不入 OSS),内容同步切片+向量化,立刻可被检索。
     */
    void createNote(Long knowledgeId, String title, String content);
}
