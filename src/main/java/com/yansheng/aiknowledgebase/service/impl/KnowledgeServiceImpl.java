package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.common.RedisKey;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import lombok.Getter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeMapper knowledgeMapper;
    @Getter
    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();
    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper,RedisTemplate<String, Object> redisTemplate) {

        this.knowledgeMapper = knowledgeMapper;
        this.redisTemplate = redisTemplate;
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

        String key= RedisKey.knowledge(id);
Object obj=null;
try {
    obj=redisTemplate.opsForValue().get(key);
    System.out.println("Redis查询："+obj);
}catch (Exception e){
    System.out.println("Redis异常:"+e.getMessage());
}

        if( "NULL".equals(obj)){
            throw new BusinessException("不存在");
        }
        if (obj != null) {
            System.out.println("走缓存");
            return (KnowledgeDetailVO) obj;
        }
        System.out.println("走MYSQL");
        KnowledgeEntity entity = knowledgeMapper.selectById(id);

        if(entity ==null){try {
            redisTemplate.opsForValue().set(key,"NULL",5,TimeUnit.MINUTES);
        }catch (Exception e){
            System.out.println("Redis写入异常");
        }

            throw new BusinessException("不存在");
        }
        KnowledgeDetailVO VO = new KnowledgeDetailVO();
        VO.setId(entity.getId());
        VO.setTitle(entity.getTitle());
        VO.setContent(entity.getContent());
        VO.setAuthor(entity.getAuthor());
        VO.setCreateTime(entity.getCreateTime());
        VO.setUpdateTime(entity.getUpdateTime());
        try {
            redisTemplate.opsForValue().set(key,VO,31+random.nextInt(5), TimeUnit.MINUTES);
        }catch (Exception e){
            System.out.println("Redis写入异常");
        }

        return VO;


    }

    @Override
    public void addKnowledge(KnowledgeAddDTO dto) {
        KnowledgeEntity knowledgeEntity = new KnowledgeEntity();
        UserEntity userEntity = UserContext.get();
        knowledgeEntity.setTitle(dto.getTitle());
        knowledgeEntity.setCategory(dto.getCategory());
        knowledgeEntity.setAuthor(userEntity.getUsername());
        knowledgeEntity.setContent(dto.getContent());
        LocalDateTime now = LocalDateTime.now();
        knowledgeEntity.setCreateTime(now);
        knowledgeEntity.setUpdateTime(now);
        knowledgeMapper.insert(knowledgeEntity);

    }

    @Override
    public void updateKnowledge(Long id, KnowledgeUpdateDTO dto) {
KnowledgeEntity knowledgeEntity = knowledgeMapper.selectById(id);

        UserEntity userEntity = UserContext.get();
if(knowledgeEntity ==null){

    throw new BusinessException("不存在");
    }
else if (!userEntity.getUsername().equals(knowledgeEntity.getAuthor())){
    throw new BusinessException("权限不足");
        }
    else {

    knowledgeEntity.setTitle(dto.getTitle());
    knowledgeEntity.setCategory(dto.getCategory());
    knowledgeEntity.setContent(dto.getContent());
    LocalDateTime now = LocalDateTime.now();
    knowledgeEntity.setUpdateTime(now);

    }
    int rows=knowledgeMapper.update(knowledgeEntity);
    if(rows<=0){
        throw new BusinessException("修改失败");
    }
        String key= RedisKey.knowledge(id);
        redisTemplate.delete(key);
    }

    @Override
    public void deleteKnowledge(Long id) {
        KnowledgeEntity knowledgeEntity = knowledgeMapper.selectById(id);
        UserEntity userEntity = UserContext.get();
        if(knowledgeEntity ==null){
            throw new BusinessException("不存在");
        }else if (!userEntity.getUsername().equals(knowledgeEntity.getAuthor())){
            throw new BusinessException("权限不足");
        }
        int rows=knowledgeMapper.delete(id);
        if(rows<=0){
            throw new BusinessException("删除失败");
        }
        String key= RedisKey.knowledge(id);
        redisTemplate.delete(key);
    }


}
