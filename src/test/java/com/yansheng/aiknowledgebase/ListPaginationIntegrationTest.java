package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * B-104 真实路径实测(本地 MySQL):新增分页 SQL 可执行、总数与既有全量查询口径一致、
 * 关键词/分类过滤与 LIMIT/OFFSET 行为符合预期。
 * 全程只读(不写库),打 integration 标签(默认回归不跑,与 AdminMapperIntegrationTest 同口径)。
 */
@SpringBootTest
@Tag("integration")
@ActiveProfiles("local")
class ListPaginationIntegrationTest {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private FileMapper fileMapper;

    /** 不存在的用户:SQL 可执行且返回空页(不依赖库内数据,恒可断言) */
    @Test
    void emptyUserPageIsEmptyAndSqlExecutes() {
        assertEquals(0L, knowledgeMapper.countPageByUserId(-1L, null, null));
        assertTrue(knowledgeMapper.selectPageByUserId(-1L, null, null, 10, 0).isEmpty());
    }

    /** 真实用户:分页总数与既有全量查询一致;首页条数 = min(size, total);越界页为空 */
    @Test
    void pageTotalMatchesFullListAndLimitsAreApplied() {
        List<KnowledgeEntity> all = knowledgeMapper.selectAll();
        assumeFalse(all.isEmpty(), "本地库无 knowledge 数据,跳过一致性断言");
        Long userId = all.get(0).getUserId();

        long total = knowledgeMapper.countPageByUserId(userId, null, null);
        assertEquals(knowledgeMapper.selectByUserId(userId).size(), total);
        assertEquals(Math.min(10, total),
                knowledgeMapper.selectPageByUserId(userId, null, null, 10, 0).size());
        assertTrue(knowledgeMapper.selectPageByUserId(userId, null, null, 10, (int) total + 10).isEmpty());
    }

    /** 关键词过滤:命中 title 或 content(用真实标题当关键词,至少命中样本自身) */
    @Test
    void keywordFilterHitsTitleOrContent() {
        List<KnowledgeEntity> all = knowledgeMapper.selectAll();
        assumeFalse(all.isEmpty(), "本地库无 knowledge 数据,跳过关键词断言");
        KnowledgeEntity sample = all.get(0);
        String keyword = sample.getTitle();

        assertTrue(knowledgeMapper.countPageByUserId(sample.getUserId(), keyword, null) >= 1);
        for (KnowledgeEntity e : knowledgeMapper.selectPageByUserId(sample.getUserId(), keyword, null, 50, 0)) {
            assertTrue(contains(e.getTitle(), keyword) || contains(e.getContent(), keyword));
        }
    }

    /** 分类过滤:命中结果分类全部一致(样本无分类则跳过) */
    @Test
    void categoryFilterReturnsOnlyThatCategory() {
        List<KnowledgeEntity> all = knowledgeMapper.selectAll();
        assumeFalse(all.isEmpty(), "本地库无 knowledge 数据,跳过分类断言");
        KnowledgeEntity sample = all.get(0);
        String category = sample.getCategory();
        assumeFalse(category == null || category.isBlank(), "样本无分类,跳过分类断言");

        assertTrue(knowledgeMapper.countPageByUserId(sample.getUserId(), null, category) >= 1);
        for (KnowledgeEntity e : knowledgeMapper.selectPageByUserId(sample.getUserId(), null, category, 50, 0)) {
            assertTrue(category.equalsIgnoreCase(e.getCategory()));
        }
    }

    /** 文件分页:总数与既有全量查询一致;LIMIT/OFFSET 生效(本地库无文件则跳过) */
    @Test
    void filePageTotalMatchesFullList() {
        FileEntity any = fileMapper.selectFirstFile();
        assumeFalse(any == null, "本地库无文件,跳过文件分页断言");
        Long knowledgeId = any.getKnowledgeId();

        long total = fileMapper.countByKnowledgeId(knowledgeId);
        assertEquals(fileMapper.selectFileByKnowledgeId(knowledgeId).size(), total);
        assertEquals(Math.min(10, total), fileMapper.selectPageByKnowledgeId(knowledgeId, 10, 0).size());
        assertTrue(fileMapper.selectPageByKnowledgeId(knowledgeId, 10, (int) total + 10).isEmpty());
    }

    private boolean contains(String s, String kw) {
        return s != null && s.contains(kw);
    }
}
