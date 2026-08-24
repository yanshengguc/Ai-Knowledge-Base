package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.service.ManualReActVerifyService;
import com.yansheng.aiknowledgebase.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 手动 ReAct 循环验证(e2e)。
 * 自建 fixture(知识容器 + 两条文件记录)再发起对话:不依赖固定 fileId 的历史数据
 * (本地库 id 会随测试清理变动,硬编码 id=1/2 曾导致用例随环境挂掉)。
 */
@Tag("e2e")
@SpringBootTest
@ActiveProfiles("local")
class ManualReActVerifyTest {

    @Autowired
    private ManualReActVerifyService manualReActVerifyService;
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private KnowledgeMapper knowledgeMapper;

    private Long knowledgeId;
    private final List<Long> fixtureIds = new ArrayList<>();

    @BeforeEach
    void setUpFixtures() {
        // file_trace 走 getFileById,现含作者归属校验(IDOR 修复),需以作者身份执行
        UserEntity user = new UserEntity();
        user.setId(0L);
        user.setUsername("react-verify");
        UserContext.set(user);

        String title = "react-verify-容器-" + System.currentTimeMillis();
        KnowledgeEntity knowledge = new KnowledgeEntity();
        knowledge.setUserId(0L);
        knowledge.setTitle(title);
        knowledge.setContent("react verify fixture");
        knowledge.setCategory("test");
        knowledge.setAuthor("react-verify");
        knowledge.setCreateTime(LocalDateTime.now());
        knowledge.setUpdateTime(LocalDateTime.now());
        knowledgeMapper.insert(knowledge);
        // insert 未回填自增主键,按 userId+title 反查
        knowledgeId = knowledgeMapper.selectByUserId(0L).stream()
                .filter(k -> title.equals(k.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("fixture 知识容器插入失败"))
                .getId();

        fixtureIds.add(insertFixture("react-verify-甲.pdf", 102_400L));
        fixtureIds.add(insertFixture("react-verify-乙.pdf", 204_800L));
    }

    @AfterEach
    void cleanFixtures() {
        for (Long id : fixtureIds) {
            fileMapper.deleteById(id);
        }
        if (knowledgeId != null) {
            knowledgeMapper.delete(knowledgeId);
        }
        UserContext.remove();
    }

    private Long insertFixture(String name, long size) {
        FileEntity file = new FileEntity();
        file.setUserId(0L);
        file.setFileName(name);
        file.setFileType("pdf");
        file.setFileSize(size);
        file.setFileUrl("http://localhost/fixture/" + name);
        file.setKnowledgeId(knowledgeId);
        file.setStatus("COMPLETED");
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        fileMapper.saveFile(file);
        return file.getId();
    }

    @Test
    void testManualLoop() {
        String prompt = "请帮我分别查一下fileId=" + fixtureIds.get(0) + "和fileId=" + fixtureIds.get(1)
                + "这两个文件的信息,并对比一下它们的大小。";

        String result = manualReActVerifyService.executeManually(prompt);

        System.out.println("=== 手动循环最终回答 ===");
        System.out.println(result);
        assert result != null && !result.isBlank() : "ReAct 循环应产出最终回答";
        // 关注控制台:">>> 手动循环第N轮" 打印了几次,和之前自动模式的2次对比
    }
}
