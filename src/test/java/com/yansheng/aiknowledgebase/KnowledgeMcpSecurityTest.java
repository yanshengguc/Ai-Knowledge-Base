package com.yansheng.aiknowledgebase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.mcp.KnowledgeMcpTools;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A1 安全回归:MCP 工具必须认证 + 按用户隔离,匿名客户端读不到任何业务数据。
 */
@SpringBootTest
@ActiveProfiles("local")
class KnowledgeMcpSecurityTest {

    @Autowired
    private KnowledgeMcpTools mcpTools;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void anonymousSearchShouldBeRejected() {
        UserContext.remove();
        assertThrows(BusinessException.class,
                () -> mcpTools.knowledge_search("测试查询"),
                "匿名 MCP 调用必须被拒绝(数据泄露口)");
    }

    @Test
    void anonymousStatsShouldBeRejected() {
        UserContext.remove();
        assertThrows(BusinessException.class,
                () -> mcpTools.knowledge_stats(),
                "匿名 MCP 统计必须被拒绝");
    }

    @Test
    void userSearchShouldOnlyReturnOwnFiles() throws Exception {
        // 取一个真实用户(库里有数据的)
        Long userId = anyUserId();
        if (userId == null) {
            return; // 无数据则跳过(环境问题)
        }
        Set<Long> ownFileIds = Set.copyOf(fileMapper.selectFileIdsByUserId(userId));
        UserContext.set(user(userId));

        String json = mcpTools.knowledge_search("测试查询");
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");
        if (results.isArray() && results.size() > 0) {
            for (JsonNode item : results) {
                Long fileId = item.path("fileId").asLong();
                assertTrue(ownFileIds.contains(fileId),
                        "检索结果必须属于当前用户,越权 fileId=" + fileId);
            }
        }
    }

    @Test
    void userStatsShouldOnlyCountOwnData() throws Exception {
        Long userId = anyUserId();
        if (userId == null) {
            return;
        }
        UserContext.set(user(userId));

        String json = mcpTools.knowledge_stats();
        JsonNode root = objectMapper.readTree(json);
        long ownKnowledge = knowledgeMapper.selectByUserId(userId).size();
        assertEquals(ownKnowledge, root.path("knowledgeCount").asLong(),
                "统计必须只含当前用户数据");
    }

    private Long anyUserId() {
        List<KnowledgeEntity> all = knowledgeMapper.selectAll();
        return all.isEmpty() ? null : all.get(0).getUserId();
    }

    private UserEntity user(Long id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername("mcp_test_user_" + id);
        return u;
    }
}
