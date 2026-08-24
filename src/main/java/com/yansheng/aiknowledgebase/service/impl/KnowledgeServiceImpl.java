package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.common.RedisKey;
import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.FileStatus;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.DocumentService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeMapper knowledgeMapper;
    private final FileMapper fileMapper;
    private final ChunkMapper chunkMapper;
    private final VectorStoreService vectorStoreService;
    private final DocumentService documentService;
    @Getter
    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();
    private static final String UNLOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] " +
            "then " +
            "return redis.call('del', KEYS[1]) " +
            "else " +
            "return 0 " +
            "end";
    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper, FileMapper fileMapper, ChunkMapper chunkMapper,
                                VectorStoreService vectorStoreService,
                                DocumentService documentService,
                                RedisTemplate<String, Object> redisTemplate) {

        this.knowledgeMapper = knowledgeMapper;
        this.fileMapper = fileMapper;
        this.chunkMapper = chunkMapper;
        this.vectorStoreService = vectorStoreService;
        this.documentService = documentService;
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

        String key = RedisKey.knowledge(id);

        KnowledgeDetailVO cached = readCache(key);
        if (cached != null) {
            log.info("走缓存");
            return cached;
        }

        String lockKey = "lock:" + key;
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                10,
                TimeUnit.SECONDS
        );

        if (Boolean.TRUE.equals(success)) {
            try {
                // 双重检查:等锁期间缓存可能已被其他线程写入
                cached = readCache(key);
                if (cached != null) {
                    log.info("走缓存");
                    return cached;
                }
                log.info("走MYSQL");
                KnowledgeDetailVO vo = loadFromDb(id);
                if (vo == null) {
                    try {
                        redisTemplate.opsForValue().set(key, "NULL", 5, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.warn("Redis写入异常");
                    }
                    throw new BusinessException("不存在");
                }
                try {
                    redisTemplate.opsForValue().set(key, vo, 31 + random.nextInt(5), TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("Redis写入异常");
                }

                return vo;

            } finally {
                redisTemplate.execute(
                        new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                        Collections.singletonList(lockKey),
                        lockValue
                );
            }
        } else {
            log.info("等待锁: id={}", id);
            Thread.sleep(100);
            for (int i = 1; i <= 3; i++) {
                Thread.sleep(100);
                // 等锁重试同样要走哨兵判断 + 归属校验(readCache 统一入口)
                KnowledgeDetailVO waited = readCache(key);
                if (waited != null) {
                    log.info("等锁后命中缓存: id={}, attempt={}/3", id, i);
                    return waited;
                }
                log.debug("等锁重试: id={}, attempt={}/3", id, i);
            }
            /*
             * 降级:等锁 ~400ms 仍未拿到缓存(持锁线程可能异常或慢查询)。
             * 互斥锁是防击穿手段,不是正确性依赖——宁可极少数请求直查 DB,
             * 不能让用户拿到"系统繁忙"。降级不写缓存,避免与持锁线程写回互相覆盖。
             */
            log.warn("锁等待超时,降级直查数据库: id={}", id);
            KnowledgeDetailVO degraded = loadFromDb(id);
            if (degraded == null) {
                throw new BusinessException("不存在");
            }
            return degraded;
        }
    }

    /**
     * 回源查库(不带缓存写入,调用方自行决定是否写缓存)。
     * 返回 null 表示不存在;归属校验不通过直接抛权限不足。
     */
    private KnowledgeDetailVO loadFromDb(Long id) {
        KnowledgeEntity entity = knowledgeMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setAuthor(entity.getAuthor());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        // 归属校验(修复越权)
        if (!UserContext.get().getUsername().equals(vo.getAuthor())) {
            throw new BusinessException("权限不足");
        }
        return vo;
    }

    /**
     * 统一的缓存读取:NULL 哨兵判断 + 归属校验,返回 null 表示未命中需回源。
     * 所有读缓存路径必须收敛到这里,防止某条并发分支漏掉归属校验或误强转哨兵。
     */
    private KnowledgeDetailVO readCache(String key) {
        Object obj;
        try {
            obj = redisTemplate.opsForValue().get(key);
            log.debug("Redis查询: {}", obj);
        } catch (Exception e) {
            log.warn("Redis异常: {}", e.getMessage());
            return null;
        }
        if ("NULL".equals(obj)) {
            throw new BusinessException("不存在");
        }
        if (obj == null) {
            return null;
        }
        // 归属校验(修复越权:知道 id 即可查看任意知识)
        KnowledgeDetailVO cachedVO = (KnowledgeDetailVO) obj;
        UserEntity userEntity = UserContext.get();
        if (!userEntity.getUsername().equals(cachedVO.getAuthor())) {
            throw new BusinessException("权限不足");
        }
        return cachedVO;
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

    @Override
    public void createNote(Long knowledgeId, String title, String content, String source) {
        // 写优先:笔记 = 特殊文件(不入 OSS),内容同步切片+向量化,写完立刻可检索
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException("笔记标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("笔记内容不能为空");
        }
        Long userId = UserContext.getUserId();
        KnowledgeEntity knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new BusinessException("不存在");
        }
        if (!userId.equals(knowledge.getUserId())) {
            throw new BusinessException("权限不足");
        }

        FileEntity note = new FileEntity();
        note.setUserId(userId);
        note.setKnowledgeId(knowledgeId);
        note.setFileName(title.trim());
        // 来源编码进 fileType:普通笔记 text/markdown;AI 对话保存 text/markdown;source=ai-chat
        // (前端列表据此显示 AI 徽标;不额外加列,避免动表结构)
        note.setFileType("text/markdown;source=" + (source == null || source.isBlank() ? "manual" : source.trim()));
        note.setFileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        note.setFileUrl(null); // 笔记无 OSS 对象
        note.setStatus(FileStatus.SUCCESS.name());
        LocalDateTime now = LocalDateTime.now();
        note.setCreateTime(now);
        note.setUpdateTime(now);
        fileMapper.saveFile(note);

        // 同步索引:切片 → 保存 chunk → 批量向量化入库
        documentService.indexPlainText(note.getId(), content);
        log.info("笔记创建并索引完成, noteFileId={}, knowledgeId={}, title={}",
                note.getId(), knowledgeId, title);
    }

    @Override
    public String exportMarkdown() {
        // 数据主权:导出当前用户全部知识 + 文件/笔记清单
        Long userId = UserContext.getUserId();
        List<KnowledgeEntity> knowledges = knowledgeMapper.selectByUserId(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("# 知识库导出\n\n");
        sb.append("- 导出时间: ").append(LocalDateTime.now()).append("\n");
        sb.append("- 知识条目数: ").append(knowledges.size()).append("\n\n");

        for (KnowledgeEntity k : knowledges) {
            sb.append("## ").append(k.getTitle()).append("\n\n");
            sb.append("**分类**: ").append(k.getCategory() == null ? "-" : k.getCategory())
                    .append(" | **作者**: ").append(k.getAuthor()).append("\n\n");
            if (k.getContent() != null && !k.getContent().isBlank()) {
                sb.append(k.getContent()).append("\n\n");
            }
            List<FileEntity> files = fileMapper.selectFileByKnowledgeId(k.getId());
            if (files != null && !files.isEmpty()) {
                sb.append("### 文件与笔记(").append(files.size()).append(")\n\n");
                for (FileEntity f : files) {
                    sb.append("- ").append(f.getFileName())
                            .append(" [").append(f.getStatus()).append("]");
                    if (f.getFileUrl() != null) {
                        sb.append(" <").append(f.getFileUrl()).append(">");
                    }
                    sb.append("\n");
                    // 笔记正文:内容就在 MySQL(切片),导出带上——数据主权不只清单,还要拿得走内容
                    if (isNote(f) && FileStatus.SUCCESS.name().equals(f.getStatus())) {
                        String noteContent = readNoteContent(f.getId());
                        if (!noteContent.isBlank()) {
                            sb.append("\n").append(indentBlockquote(noteContent)).append("\n\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        sb.append("---\n*由 Ai-Knowledge-Base 导出*");
        return sb.toString();
    }

    private boolean isNote(FileEntity f) {
        String type = f.getFileType();
        return type != null && type.startsWith("text/markdown");
    }

    /** 笔记正文 = 该文件全部切片按序拼接(笔记经 indexPlainText 落库,内容只在 chunk 表) */
    private String readNoteContent(Long fileId) {
        List<ChunkEntity> chunks = chunkMapper.selectByFileId(fileId);
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return chunks.stream()
                .sorted(Comparator.comparingInt(ChunkEntity::getChunkIndex))
                .map(ChunkEntity::getContent)
                .collect(Collectors.joining("\n"));
    }

    /** 笔记正文缩进为引用块:与文件清单行视觉区分,导出的 md 仍是一份合法 Markdown */
    private String indentBlockquote(String content) {
        return Arrays.stream(content.strip().split("\n", -1))
                .map(line -> "> " + line)
                .collect(Collectors.joining("\n"));
    }


}
