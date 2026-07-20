package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {
    private final KnowledgeMapper knowledgeMapper;
    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper) {

        this.knowledgeMapper = knowledgeMapper;
    }


    @Override
    public List<KnowledgeVO> getKnowledgeList() {
        List<KnowledgeEntity> knowledgeList = knowledgeMapper.selectAll();
        List<KnowledgeVO> result = new ArrayList<>();

        for (KnowledgeEntity knowledgeEntity : knowledgeList) {
            KnowledgeVO knowledgeVO = new KnowledgeVO();
            knowledgeVO.setId(knowledgeEntity.getId());
            knowledgeVO.setTitle(knowledgeEntity.getTitle());
            knowledgeVO.setCategory(knowledgeEntity.getCategory());
            knowledgeVO.setAuthor(knowledgeEntity.getAuthor());
            knowledgeVO.setCreateTime(knowledgeEntity.getCreateTime());
            knowledgeVO.setUpdateTime(knowledgeEntity.getUpdateTime());
            result.add(knowledgeVO);

        }
        return result;
    }

    @Override
    public KnowledgeDetailVO getKnowledgeById(Long id) {

        KnowledgeEntity entity = knowledgeMapper.selectById(id);
        KnowledgeDetailVO VO = new KnowledgeDetailVO();
        VO.setId(entity.getId());
        VO.setTitle(entity.getTitle());
        VO.setContent(entity.getContent());
        VO.setAuthor(entity.getAuthor());
        VO.setCreateTime(entity.getCreateTime());
        VO.setUpdateTime(entity.getUpdateTime());
        return VO;

    }

    @Override
    public void addKnowledge(KnowledgeAddDTO dto) {
        KnowledgeEntity knowledgeEntity = new KnowledgeEntity();
        knowledgeEntity.setTitle(dto.getTitle());
        knowledgeEntity.setCategory(dto.getCategory());
        knowledgeEntity.setAuthor(dto.getAuthor());
        knowledgeEntity.setContent(dto.getContent());
        LocalDateTime now = LocalDateTime.now();
        knowledgeEntity.setCreateTime(now);
        knowledgeEntity.setUpdateTime(now);
        knowledgeMapper.insert(knowledgeEntity);

    }


}
