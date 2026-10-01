package com.yansheng.aiknowledgebase;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.yansheng.aiknowledgebase.service.splitter.StructureAwareSplitter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-114 回填可行性探针(只读对账)。
 *
 * 验证问题:把 OSS 原文用【当前】StructureAwareSplitter(500,100) 重跑,
 * 能否与库内 knowledge_chunk 逐条一致。
 *
 * 只读保证:仅 SELECT + OSS GET,不写库、不调 LLM、不启 Spring 上下文(故不依赖 Redis)。
 * 打 integration 标签:依赖本地 MySQL + OSS 凭据,默认回归不跑。
 *
 * 运行:
 *   mvn test -Dtest=B114BackfillAlignmentProbeTest -DexcludedGroups=e2e
 */
@Tag("integration")
class B114BackfillAlignmentProbeTest {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 100;

    @Test
    void reconcileOssOriginalAgainstStoredChunks() throws Exception {
        Properties base = load("src/main/resources/application.properties");
        Properties local = load("src/main/resources/application-local.properties");

        String url = base.getProperty("spring.datasource.url");
        String dbUser = base.getProperty("spring.datasource.username");
        String dbPwd = local.getProperty("spring.datasource.password");
        String endpoint = base.getProperty("aliyun.oss.endpoint");
        String bucket = base.getProperty("aliyun.oss.bucket-name");
        String ak = local.getProperty("aliyun.oss.access-key-id");
        String sk = local.getProperty("aliyun.oss.access-key-secret");

        StructureAwareSplitter splitter = new StructureAwareSplitter(CHUNK_SIZE, OVERLAP);
        OSS oss = new OSSClientBuilder().build(endpoint, ak, sk);

        StringBuilder report = new StringBuilder();
        report.append("\n===== B-114 回填对账报告(txt=.md, 有 OSS 原文, status=SUCCESS) =====\n");
        report.append(String.format("切片器口径: StructureAwareSplitter(chunkSize=%d, overlap=%d)%n", CHUNK_SIZE, OVERLAP));

        int filesProcessed = 0;
        int filesFullyIdentical = 0;
        int totalChunks = 0;
        int totalMatched = 0;

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPwd)) {
            for (Map<String, Object> f : mdFilesWithOriginal(conn)) {
                long fileId = ((Number) f.get("id")).longValue();
                String fileName = String.valueOf(f.get("file_name"));
                String fileUrl = String.valueOf(f.get("file_url"));

                String original;
                try {
                    original = download(oss, bucket, endpoint, fileUrl);
                } catch (Exception e) {
                    report.append(String.format("%n[file_id=%d] %s%n  ! 原文下载失败: %s%n",
                            fileId, fileName, e.getMessage()));
                    continue;
                }

                List<String> rerun = splitter.split(original);
                List<String> stored = storedChunks(conn, fileId);

                int matched = 0;
                int firstMismatch = -1;
                for (int i = 0; i < Math.max(rerun.size(), stored.size()); i++) {
                    String a = i < rerun.size() ? normalize(rerun.get(i)) : null;
                    String b = i < stored.size() ? normalize(stored.get(i)) : null;
                    if (a != null && a.equals(b)) {
                        matched++;
                    } else if (firstMismatch < 0) {
                        firstMismatch = i;
                    }
                }

                filesProcessed++;
                totalChunks += Math.max(rerun.size(), stored.size());
                totalMatched += matched;
                if (rerun.size() == stored.size() && matched == rerun.size()) {
                    filesFullyIdentical++;
                }

                report.append(String.format("%n[file_id=%d] %s%n", fileId, fileName));
                report.append(String.format("  原始字符数=%d  库内 chunk=%d  重跑 chunk=%d  逐条一致=%d%n",
                        original.length(), stored.size(), rerun.size(), matched));
                if (firstMismatch >= 0) {
                    report.append(String.format("  首个不一致 index=%d%n", firstMismatch));
                    report.append(String.format("    库内: %s%n", describe(stored, firstMismatch)));
                    report.append(String.format("    重跑: %s%n", describe(rerun, firstMismatch)));
                }
            }
        } finally {
            oss.shutdown();
        }

        report.append(String.format("%n===== 汇总: 处理 %d 个文件, 完全一致 %d 个, 逐条一致率 %d/%d%n",
                filesProcessed, filesFullyIdentical, totalMatched, totalChunks));
        System.out.println(report);

        assertTrue(filesProcessed > 0, "未取到任何可对账的 md 文件(file_url 非空 + status=SUCCESS + 有 chunk)");
    }

    private static List<Map<String, Object>> mdFilesWithOriginal(Connection conn) throws Exception {
        String sql = "SELECT f.id, f.file_name, f.file_url, COUNT(c.id) AS chunks "
                + "FROM knowledge_file f JOIN knowledge_chunk c ON c.file_id = f.id "
                + "WHERE f.status = 'SUCCESS' AND f.file_url IS NOT NULL AND f.file_name LIKE '%.md' "
                + "GROUP BY f.id, f.file_name, f.file_url ORDER BY f.id";
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("file_name", rs.getString("file_name"));
                row.put("file_url", rs.getString("file_url"));
                out.add(row);
            }
        }
        return out;
    }

    private static List<String> storedChunks(Connection conn, long fileId) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT content FROM knowledge_chunk WHERE file_id = ? ORDER BY chunk_index")) {
            ps.setLong(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static String download(OSS oss, String bucket, String endpoint, String fileUrl) throws Exception {
        String prefix = "https://" + bucket + "." + endpoint + "/";
        String key = fileUrl.startsWith(prefix) ? fileUrl.substring(prefix.length()) : fileUrl;
        if (key.contains("?")) {
            key = key.substring(0, key.indexOf("?"));
        }
        try (InputStream in = oss.getObject(bucket, key).getObjectContent();
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) {
                buf.write(b, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    /** 与 splitter 产出口径对齐:统一 LF,去行尾空白 */
    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
    }

    private static String describe(List<String> list, int idx) {
        if (idx >= list.size()) {
            return "<该侧无此 index>";
        }
        String s = normalize(list.get(idx));
        String head = s.length() > 70 ? s.substring(0, 70) + "..." : s;
        return String.format("len=%d head=%s", s.length(), head.replace("\n", "\\n"));
    }

    private static Properties load(String path) throws Exception {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            p.load(in);
        }
        return p;
    }
}