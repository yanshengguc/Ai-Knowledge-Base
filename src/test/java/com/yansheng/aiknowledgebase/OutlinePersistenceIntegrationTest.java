package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.entity.OutlineChunkRefEntity;
import com.yansheng.aiknowledgebase.entity.OutlineNodeEntity;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.OutlineMapper;
import com.yansheng.aiknowledgebase.service.impl.OutlineIndexServiceImpl;
import com.yansheng.aiknowledgebase.service.splitter.StructureAwareSplitter;
import com.yansheng.aiknowledgebase.vo.OutlineSourceChunkVO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-114 Phase1 真实数据验证(只读回滚,不落库)。
 *
 * 验证 Mapper XML 对真实 MySQL 是否可跑:批量插入 + useGeneratedKeys 回填 id、
 * parent_id 回填(CASE WHEN 单语句)、幂等重建(先删后插)、节点↔chunk 关联 join。
 *
 * 刻意不启 Spring 上下文(裸 MyBatis),故不依赖 Redis;
 * 全部写入在同一事务内,结束时 rollback,不留数据。
 *
 * 运行:
 *   mvn test -Dtest=OutlinePersistenceIntegrationTest -DexcludedGroups=e2e -DfailIfNoTests=false
 */
@Tag("integration")
class OutlinePersistenceIntegrationTest {

    private static final String MARKDOWN = """
            # 架构

            总览正文。

            ## 接入层

            接入层正文。

            ### 路由

            路由正文。

            ## 空父标题

            ### 有正文的子节

            子节正文。
            """;

    @Test
    void 真实MySQL下生成标题树并幂等重建() throws Exception {
        Properties base = load("src/main/resources/application.properties");
        Properties local = load("src/main/resources/application-local.properties");

        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver",
                base.getProperty("spring.datasource.url"),
                base.getProperty("spring.datasource.username"),
                local.getProperty("spring.datasource.password"));

        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parseMapper(configuration, "mapper/OutlineMapper.xml");
        parseMapper(configuration, "mapper/ChunkMapper.xml");
        configuration.setEnvironment(new Environment("outline-it", new JdbcTransactionFactory(), dataSource));
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        SqlSession session = factory.openSession(false);
        try {
            Connection conn = session.getConnection();
            long knowledgeId = insert(conn,
                    "INSERT INTO knowledge (title, author, user_id) VALUES (?,?,?)",
                    "outline-it", "outline-it-user", 999001L);
            long fileId = insert(conn,
                    "INSERT INTO knowledge_file (user_id, file_name, file_type, file_url, knowledge_id, status) "
                            + "VALUES (?,?,?,?,?,?)",
                    999001L, "outline-it.md", "text/markdown", "https://example.com/outline-it.md",
                    knowledgeId, "SUCCESS");

            List<String> parts = new StructureAwareSplitter(500, 100).split(MARKDOWN);
            for (int i = 0; i < parts.size(); i++) {
                insert(conn,
                        "INSERT INTO knowledge_chunk (file_id, chunk_index, content, content_length) VALUES (?,?,?,?)",
                        fileId, i, parts.get(i), parts.get(i).length());
            }

            OutlineMapper outlineMapper = session.getMapper(OutlineMapper.class);
            ChunkMapper chunkMapper = session.getMapper(ChunkMapper.class);
            OutlineIndexServiceImpl service =
                    new OutlineIndexServiceImpl(outlineMapper, chunkMapper, null, null, null);

            int nodeCount = service.indexFile(fileId, MARKDOWN);
            assertEquals(5, nodeCount, "5 个标题节点(含空父标题)");

            List<OutlineNodeEntity> nodes = outlineMapper.selectNodesByFileId(fileId);
            assertEquals(5, nodes.size());
            assertEquals(nodes.get(0).getId(), nodes.get(1).getParentId(), "parent_id 回填(架构 → 接入层)");
            assertEquals(nodes.get(1).getId(), nodes.get(2).getParentId());
            assertEquals(nodes.get(3).getId(), nodes.get(4).getParentId(), "空父标题仍入树并作父节点");
            assertEquals("架构 / 接入层 / 路由", nodes.get(2).getHeadingPath());

            Map<Long, Integer> nodeIndexById = new HashMap<>();
            nodes.forEach(n -> nodeIndexById.put(n.getId(), n.getNodeIndex()));
            List<OutlineChunkRefEntity> refs = outlineMapper.selectRefsByFileId(fileId);
            assertEquals(List.of(0, 1, 2, 4),
                    refs.stream().map(r -> nodeIndexById.get(r.getNodeId())).sorted().toList(),
                    "空父标题(nodeIndex=3)不产生关联");

            List<OutlineSourceChunkVO> sources = outlineMapper.selectRefsByNodeId(nodes.get(0).getId());
            assertEquals(1, sources.size());
            assertNotNull(sources.get(0).getContentLength());
            assertTrue(sources.get(0).getPreview().startsWith("# 架构"), "溯源指向真实 chunk 正文");

            // 幂等:重跑不叠加
            assertEquals(5, service.indexFile(fileId, MARKDOWN));
            assertEquals(5, outlineMapper.selectNodesByFileId(fileId).size(), "重建先删后插,节点不叠加");
            assertEquals(parts.size(), outlineMapper.selectRefsByFileId(fileId).size(), "无标题前导数不变,关联数=切片数");

            // 级联:删文件(节点外键 ON DELETE CASCADE)
            assertEquals(1, conn.prepareStatement("DELETE FROM knowledge_file WHERE id = " + fileId).executeUpdate());
            // 裸 JDBC 改动不经 MyBatis,需清一级缓存才能看到级联结果
            session.clearCache();
            session.clearCache(); assertEquals(0, outlineMapper.selectNodesByFileId(fileId).size(), "删文件级联清空导航层(裸JDBC改动不经MyBatis,需清一级缓存)");
        } finally {
            session.rollback();
            session.close();
        }
    }

    private static void parseMapper(org.apache.ibatis.session.Configuration configuration, String resource)
            throws Exception {
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static long insert(Connection conn, String sql, Object... args) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next(), "未取到自增 id");
                return rs.getLong(1);
            }
        }
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            properties.load(in);
        }
        return properties;
    }
}