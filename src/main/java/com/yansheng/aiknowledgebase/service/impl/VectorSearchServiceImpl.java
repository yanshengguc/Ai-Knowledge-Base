package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.SearchResult;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.service.EmbeddingService;
import com.yansheng.aiknowledgebase.service.VectorSearchService;
import com.yansheng.aiknowledgebase.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchServiceImpl.class);

    /** query embedding 缓存 TTL:同一问题的向量化结果可复用(embedding 与用户无关) */
    private static final long EMBED_CACHE_TTL_MINUTES = 60;

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FileMapper fileMapper;

    public VectorSearchServiceImpl(EmbeddingService embeddingService,
                                   VectorStoreService vectorStoreService,
                                   RedisTemplate<String, Object> redisTemplate,
                                   FileMapper fileMapper) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.redisTemplate = redisTemplate;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<SearchResult> search(String query, int topK) {
        // 查询向量带缓存:相同问题不重复调 embedding API
        float[] queryVector = embedWithCache(query);
        return vectorStoreService.search(queryVector, topK);
    }

    @Override
    public List<SearchResult> searchForUser(String query, int topK, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        // 用户拥有的文件范围 → filter 表达式,向量检索只在该范围内召回(多用户隔离)
        List<Long> fileIds = fileMapper.selectFileIdsByUserId(userId);
        if (fileIds == null || fileIds.isEmpty()) {
            return new ArrayList<>();
        }
        String filter = fileIds.stream()
                .map(id -> "file_id = " + id)
                .collect(Collectors.joining(" OR "));

        float[] queryVector = embedWithCache(query);
        return vectorStoreService.search(queryVector, topK, filter);
    }

    private float[] embedWithCache(String query) {
        String key = "embed:q:" + md5(query.trim().toLowerCase());

        Object cached = null;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("query向量缓存读取失败, key={}, error={}", key, e.getMessage());
        }
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Number) {
            return toFloatArray(list);
        }

        float[] vector = embeddingService.embed(query);

        // 写缓存统一存 List<Float>(float[] 经 JSON 序列化往返不可靠,统一转列表)
        try {
            List<Float> floats = new ArrayList<>(vector.length);
            for (float v : vector) {
                floats.add(v);
            }
            redisTemplate.opsForValue().set(key, floats, EMBED_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("query向量缓存写入失败, key={}, error={}", key, e.getMessage());
        }
        return vector;
    }

    private float[] toFloatArray(List<?> numbers) {
        float[] vector = new float[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            vector[i] = ((Number) numbers.get(i)).floatValue();
        }
        return vector;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
