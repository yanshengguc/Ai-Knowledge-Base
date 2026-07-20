package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;

import java.util.List;

public interface KnowledgeService {
    List<KnowledgeVO> getKnowledgeList();

 KnowledgeDetailVO getKnowledgeById(Long id);
  void addKnowledge(KnowledgeAddDTO dto);
  void  updateKnowledge(Long id, KnowledgeUpdateDTO dto);
}
