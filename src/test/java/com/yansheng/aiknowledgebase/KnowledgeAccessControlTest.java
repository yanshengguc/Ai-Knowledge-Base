package com.yansheng.aiknowledgebase;


import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限隔离回归测试(P1 列表 / P2 详情 / P3 更新 / P4 删除)
 * 验证 2026-08-17 越权修复真实生效,断言数据正确性而不只是"抛没抛异常"
 */
@SpringBootTest
@ActiveProfiles("local")
class KnowledgeAccessControlTest {

    @Autowired
    private KnowledgeService knowledgeService;

    private UserEntity userA;
    private UserEntity userB;
    private String titleA;
    private Long knowledgeIdA;
    private Long knowledgeIdB;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        titleA = "perm-title-A-" + ts;
        String titleB = "perm-title-B-" + ts;
        userA = new UserEntity();
        userA.setId(ts);
        userA.setUsername("perm-user-A-" + ts);
        userB = new UserEntity();
        userB.setId(ts + 1);
        userB.setUsername("perm-user-B-" + ts);

        // A 建一条知识
        UserContext.set(userA);
        knowledgeService.addKnowledge(buildAddDTO(titleA, "content of A"));
        knowledgeIdA = findKnowledgeId(titleA);

        // B 建一条知识
        UserContext.set(userB);
        knowledgeService.addKnowledge(buildAddDTO(titleB, "content of B"));
        knowledgeIdB = findKnowledgeId(titleB);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private KnowledgeAddDTO buildAddDTO(String title, String content) {
        KnowledgeAddDTO dto = new KnowledgeAddDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setCategory("perm-test");
        return dto;
    }

    private KnowledgeUpdateDTO buildUpdateDTO(String title, String content) {
        KnowledgeUpdateDTO dto = new KnowledgeUpdateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        return dto;
    }

    private Long findKnowledgeId(String title) {
        List<KnowledgeVO> list = knowledgeService.getKnowledgeList();
        return list.stream()
                .filter(v -> title.equals(v.getTitle()))
                .findFirst()
                .map(KnowledgeVO::getId)
                .orElseThrow(() -> new AssertionError("未找到测试知识: " + title));
    }

    @Test
    void testListContainsOnlyOwnKnowledge() {
        // P1:列表隔离——A 的列表只能看到 A 自己的知识
        UserContext.set(userA);
        List<KnowledgeVO> listA = knowledgeService.getKnowledgeList();
        assertFalse(listA.isEmpty(), "A 应至少看到自己的知识");
        assertTrue(listA.stream().allMatch(v -> userA.getUsername().equals(v.getAuthor())),
                "A 的列表中不应出现其他用户的知识");

        UserContext.set(userB);
        List<KnowledgeVO> listB = knowledgeService.getKnowledgeList();
        assertTrue(listB.stream().allMatch(v -> userB.getUsername().equals(v.getAuthor())),
                "B 的列表中不应出现其他用户的知识");
    }

    @Test
    void testGetOwnKnowledgeDetailSuccess() throws Exception {
        // P2 正常:查自己的知识,内容正确
        UserContext.set(userA);
        KnowledgeDetailVO vo = knowledgeService.getKnowledgeById(knowledgeIdA);
        assertEquals("content of A", vo.getContent());
        assertEquals(userA.getUsername(), vo.getAuthor());
    }

    @Test
    void testGetOthersKnowledgeDetailDenied() throws Exception {
        // P2 越权:B 查 A 的知识 → 权限不足
        UserContext.set(userB);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.getKnowledgeById(knowledgeIdA));
        assertEquals("权限不足", ex.getMessage());
    }

    @Test
    void testGetOthersKnowledgeDeniedEvenWhenCached() throws Exception {
        // P2 缓存分支:A 先查(写入缓存)→ B 再查同一 id → 缓存命中也应被拒
        UserContext.set(userA);
        knowledgeService.getKnowledgeById(knowledgeIdA);  // 入缓存
        UserContext.set(userB);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.getKnowledgeById(knowledgeIdA));
        assertEquals("权限不足", ex.getMessage());
    }

    @Test
    void testUpdateOthersKnowledgeDeniedAndDataUnchanged() throws Exception {
        // P3 越权更新:B 改 A 的知识 → 权限不足,且查库确认数据未被修改
        UserContext.set(userB);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.updateKnowledge(knowledgeIdA, buildUpdateDTO("hacked-title", "hacked")));
        assertEquals("权限不足", ex.getMessage());

        UserContext.set(userA);
        KnowledgeDetailVO vo = knowledgeService.getKnowledgeById(knowledgeIdA);
        assertEquals(titleA, vo.getTitle(), "越权更新后数据必须保持不变");
    }

    @Test
    void testDeleteOthersKnowledgeDeniedAndDataRemains() {
        // P4 越权删除:B 删 A 的知识 → 权限不足,数据仍在
        UserContext.set(userB);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.deleteKnowledge(knowledgeIdA));
        assertEquals("权限不足", ex.getMessage());

        UserContext.set(userA);
        List<KnowledgeVO> listA = knowledgeService.getKnowledgeList();
        assertTrue(listA.stream().anyMatch(v -> titleA.equals(v.getTitle())),
                "越权删除后知识必须仍在");
    }

    @Test
    void testDeleteNonexistentIdThrowsNotFound() {
        // 边界:删除不存在的 id
        UserContext.set(userA);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.deleteKnowledge(999999999L));
        assertEquals("不存在", ex.getMessage());
    }
}
