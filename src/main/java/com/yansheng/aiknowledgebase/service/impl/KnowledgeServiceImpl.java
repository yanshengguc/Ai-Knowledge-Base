package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.common.RedisKey;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ChunkMapper chunkMapper;
    private final VectorStoreService vectorStoreService;
    @Getter
    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();
    private final String lockValue = UUID.randomUUID().toString();
    private static final String UNLOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] " +
            "then " +
            "return redis.call('del', KEYS[1]) " +
            "else " +
            "return 0 " +
            "end";
    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper, FileMapper fileMapper, ChunkMapper chunkMapper,
                                VectorStoreService vectorStoreService,
                                RedisTemplate<String, Object> redisTemplate) {

        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.vectorStoreService = vectorStoreService;
        this.redisTemplate = redisTemplate;

    }



    @Override
    public List<KnowledgeVO> getKnowledgeList() {
        // 只返回当前用户自己的知识(修复越权:此前 selectAll 返回全部用户的知识)
        List<KnowledgeEntity> knowledgeList = knowledgeMapper.selectByUserId(UserContext.getUserId());
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
    public KnowledgeDetailVO getKnowledgeById(Long id) throws InterruptedException {

        String key= RedisKey.knowledge(id);
Object obj=null;
        try {
            obj=redisTemplate.opsForValue().get(key);
            log.debug("Redis查询: {}", obj);
        }catch (Exception e){
            log.warn("Redis异常: {}", e.getMessage());
        }
        if( "NULL".equals(obj)){
            throw new BusinessException("不存在");
        }
        if (obj != null) {
            log.info("走缓存");
            // 归属校验(修复越权:知道 id 即可查看任意知识)
            KnowledgeDetailVO cachedVO = (KnowledgeDetailVO) obj;
            UserEntity userEntity = UserContext.get();
            if (!userEntity.getUsername().equals(cachedVO.getAuthor())) {
                throw new BusinessException("权限不足");
            }
            return cachedVO;
        }
        String lockKey = "lock:"+key;
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                10,
                TimeUnit.SECONDS
        );

        if (Boolean.TRUE.equals(success)) {
            try {
                obj=redisTemplate.opsForValue().get(key);
                if( "NULL".equals(obj)){
                    throw new BusinessException("不存在");
                }
                if (obj != null) {
                    log.info("走缓存");
                    // 归属校验(修复越权)
                    KnowledgeDetailVO cachedVO2 = (KnowledgeDetailVO) obj;
                    if (!UserContext.get().getUsername().equals(cachedVO2.getAuthor())) {
                        throw new BusinessException("权限不足");
                    }
                    return cachedVO2;
                }
                log.info("走MYSQL");
                KnowledgeEntity entity = knowledgeMapper.selectById(id);

                if(entity ==null){try {
                    redisTemplate.opsForValue().set(key,"NULL",5,TimeUnit.MINUTES);
                }catch (Exception e){
                    log.warn("Redis写入异常");
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
                // 归属校验(修复越权)
                if (!UserContext.get().getUsername().equals(entity.getAuthor())) {
                    throw new BusinessException("权限不足");
                }
                try {
                    redisTemplate.opsForValue().set(key,VO,31+random.nextInt(5), TimeUnit.MINUTES);
                }catch (Exception e){
                    log.warn("Redis写入异常");
                }

                return VO;

            }finally {
                redisTemplate.execute(
                        new
                                DefaultRedisScript<>(UNLOCK_SCRIPT,
                                Long.class
                        ),
                        Collections.singletonList(lockKey),
                        lockValue
                );
}
            }else{
            log.info("等待锁");
            try {
Thread.sleep(100);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            for(int i=0;i<3;i++){

                Thread.sleep(100);

                obj = redisTemplate.opsForValue().get(key);

                if(obj != null){
                    return (KnowledgeDetailVO)obj;
                }

            }
            throw new BusinessException("系统繁忙");
        }
        }

    @Override
    public void addKnowledge(KnowledgeAddDTO dto) {
        // 参数校验:防"POST /knowledge {action:list}"这类非法请求写脏数据(空知识)
        if (dto == null || dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BusinessException("标题不能为空");
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BusinessException("内容不能为空");
        }
        KnowledgeEntity knowledgeEntity = new KnowledgeEntity();
        UserEntity userEntity = UserContext.get();
        knowledgeEntity.setTitle(dto.getTitle());
        knowledgeEntity.setCategory(dto.getCategory());
        knowledgeEntity.setAuthor(userEntity.getUsername());
        knowledgeEntity.setContent(dto.getContent());
        LocalDateTime now = LocalDateTime.now();
        knowledgeEntity.setCreateTime(now);
        knowledgeEntity.setUpdateTime(now);
        knowledgeEntity.setUserId(UserContext.getUserId());
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
        // 级联删除:先删切片 → 再删文件 → 最后删知识(避免外键约束报错)
        List<FileEntity> files = fileMapper.selectFileByKnowledgeId(id);
        for (FileEntity file : files) {
            chunkMapper.deleteByFileId(file.getId());
        }
        fileMapper.deleteByKnowledgeId(id);
        int rows=knowledgeMapper.delete(id);
        if(rows<=0){
            throw new BusinessException("删除失败");
        }
        // 清理向量库(防"删了还能搜到"):每个文件按 file_id 删 DashVector
        for (FileEntity file : files) {
            vectorStoreService.deleteByFileId(file.getId());
        }
        String key= RedisKey.knowledge(id);
        redisTemplate.delete(key);
    }


}
