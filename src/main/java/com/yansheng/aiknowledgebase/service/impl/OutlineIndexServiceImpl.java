package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.ChunkEntity;
import com.yansheng.aiknowledgebase.entity.FileEntity;
import com.yansheng.aiknowledgebase.entity.KnowledgeEntity;
import com.yansheng.aiknowledgebase.entity.OutlineChunkRefEntity;
import com.yansheng.aiknowledgebase.entity.OutlineNodeEntity;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.mapper.ChunkMapper;
import com.yansheng.aiknowledgebase.mapper.FileMapper;
import com.yansheng.aiknowledgebase.mapper.KnowledgeMapper;
import com.yansheng.aiknowledgebase.mapper.OutlineMapper;
import com.yansheng.aiknowledgebase.service.OutlineIndexService;
import com.yansheng.aiknowledgebase.service.OssService;
import com.yansheng.aiknowledgebase.service.splitter.MarkdownOutlineParser;
import com.yansheng.aiknowledgebase.utils.UserContext;
import com.yansheng.aiknowledgebase.vo.OutlineNodeDetailVO;
import com.yansheng.aiknowledgebase.vo.OutlineNodeVO;
import com.yansheng.aiknowledgebase.vo.OutlineSourceChunkVO;
import com.yansheng.aiknowledgebase.vo.OutlineTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * B-114 Outline 导航层实现(Phase1,后端最小闭环)。
 *
 * 关联构造(核心技术决策,勿改成"按标题文本全局匹配"):
 *  - StructureAwareSplitter 每个 chunk = heading + "\n" + body,首行即所属标题行
 *  - splitter 给【同节每个 chunk】都前置标题(不只首块),故"标题重复 = 同节续块"
 *  - "有正文"的 Outline 节点序列 与 chunk 的标题序列 一一顺序对应
 *  - 因此按顺序对齐 + 标题行逐字校验;一旦对不齐或"同名新节 vs 同节续块"无法区分:
 *    停止关联并打 WARN,不猜不猜错(宁可少关联,不可错关联)
 */
@Slf4j
@Service
public class OutlineIndexServiceImpl implements OutlineIndexService {

    /** 与 StructureAwareSplitter.HEADING 严格一致:不接受缩进标题(解析器允许 3 空格缩进,splitter 不认) */
    private static final Pattern SPLITTER_HEADING = Pattern.compile("^#{1,6}\\s.*");
    private static final String HEADING_PATH_SEPARATOR = " / ";
    private static final int PREVIEW_LENGTH = 200;

    private final OutlineMapper outlineMapper;
    private final ChunkMapper chunkMapper;
    private final FileMapper fileMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final OssService ossService;
    private final MarkdownOutlineParser outlineParser = new MarkdownOutlineParser();

    public OutlineIndexServiceImpl(OutlineMapper outlineMapper,
                                   ChunkMapper chunkMapper,
                                   FileMapper fileMapper,
                                   KnowledgeMapper knowledgeMapper,
                                   OssService ossService) {
        this.outlineMapper = outlineMapper;
        this.chunkMapper = chunkMapper;
        this.fileMapper = fileMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.ossService = ossService;
    }

    @Override
    public int indexFile(Long fileId, String markdown) {
        // 幂等:先清旧导航层(外键级联清关联),再按当前原文重建
        outlineMapper.deleteNodesByFileId(fileId);

        List<MarkdownOutlineParser.MarkdownOutlineNode> parsed = outlineParser.parse(markdown);
        if (parsed.isEmpty()) {
            log.info("Outline 导航层:原文无标题,fileId={}", fileId);
            return 0;
        }

        List<OutlineNodeEntity> nodes = new ArrayList<>();
        List<Integer> parentIndexes = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < parsed.size(); i++) {
            MarkdownOutlineParser.MarkdownOutlineNode parsedNode = parsed.get(i);
            while (!stack.isEmpty() && parsed.get(stack.peek()).level() >= parsedNode.level()) {
                stack.pop();
            }
            parentIndexes.add(stack.isEmpty() ? null : stack.peek());

            OutlineNodeEntity node = new OutlineNodeEntity();
            node.setFileId(fileId);
            node.setNodeIndex(i);
            node.setLevel(parsedNode.level());
            node.setTitle(parsedNode.title());
            node.setHeadingPath(String.join(HEADING_PATH_SEPARATOR, parsedNode.headingPath()));
            node.setSourceStartOffset(parsedNode.sourceStartOffset());
            node.setSourceEndOffset(parsedNode.sourceEndOffset());
            node.setCreateTime(now);
            nodes.add(node);
            stack.push(i);
        }

        // 先序插入:父节点必先于子节点,插入后 id 才可用,再回填 parent_id
        outlineMapper.insertNodes(nodes);
        List<OutlineNodeEntity> children = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Integer parentIndex = parentIndexes.get(i);
            if (parentIndex != null) {
                nodes.get(i).setParentId(nodes.get(parentIndex).getId());
                children.add(nodes.get(i));
            }
        }
        if (!children.isEmpty()) {
            outlineMapper.updateParents(children);
        }

        List<OutlineChunkRefEntity> refs =
                buildRefs(fileId, nodes, markdown, chunkMapper.selectByFileId(fileId));
        if (!refs.isEmpty()) {
            outlineMapper.insertRefs(refs);
        }

        log.info("Outline 导航层生成完成,fileId={},nodeCount={},refCount={}", fileId, nodes.size(), refs.size());
        return nodes.size();
    }

    @Override
    public int rebuild(Long fileId) {
        FileEntity file = requireFile(fileId);
        verifyOwnership(file.getKnowledgeId());
        if (!isMarkdown(file.getFileName())) {
            throw new BusinessException("仅 md 文件支持生成标题导航层");
        }
        String text = ossService.getContent(file.getFileUrl());
        if (text == null || text.isBlank()) {
            throw new BusinessException("原文不可用,无法生成标题导航层");
        }
        return indexFile(fileId, text);
    }

    @Override
    public OutlineTreeVO getTree(Long fileId) {
        FileEntity file = requireFile(fileId);
        verifyOwnership(file.getKnowledgeId());

        Map<Long, Integer> refCounts = new HashMap<>();
        for (OutlineChunkRefEntity ref : outlineMapper.selectRefsByFileId(fileId)) {
            refCounts.merge(ref.getNodeId(), 1, Integer::sum);
        }

        List<OutlineNodeVO> nodes = new ArrayList<>();
        for (OutlineNodeEntity entity : outlineMapper.selectNodesByFileId(fileId)) {
            OutlineNodeVO vo = new OutlineNodeVO();
            vo.setId(entity.getId());
            vo.setParentId(entity.getParentId());
            vo.setNodeIndex(entity.getNodeIndex());
            vo.setLevel(entity.getLevel());
            vo.setTitle(entity.getTitle());
            vo.setHeadingPath(entity.getHeadingPath());
            vo.setSourceStartOffset(entity.getSourceStartOffset());
            vo.setSourceEndOffset(entity.getSourceEndOffset());
            vo.setSourceChunkCount(refCounts.getOrDefault(entity.getId(), 0));
            nodes.add(vo);
        }

        OutlineTreeVO tree = new OutlineTreeVO();
        tree.setFileId(fileId);
        tree.setFileName(file.getFileName());
        tree.setNodeCount(nodes.size());
        tree.setNodes(nodes);
        return tree;
    }

    @Override
    public OutlineNodeDetailVO getNodeDetail(Long nodeId) {
        OutlineNodeEntity entity = outlineMapper.selectNodeById(nodeId);
        if (entity == null) {
            throw new BusinessException("节点不存在");
        }
        FileEntity file = requireFile(entity.getFileId());
        verifyOwnership(file.getKnowledgeId());

        List<OutlineSourceChunkVO> refs = outlineMapper.selectRefsByNodeId(nodeId);
        for (OutlineSourceChunkVO ref : refs) {
            ref.setPreview(preview(ref.getPreview()));
        }

        OutlineNodeDetailVO vo = new OutlineNodeDetailVO();
        vo.setId(entity.getId());
        vo.setFileId(entity.getFileId());
        vo.setNodeIndex(entity.getNodeIndex());
        vo.setLevel(entity.getLevel());
        vo.setTitle(entity.getTitle());
        vo.setHeadingPath(entity.getHeadingPath());
        vo.setSourceChunks(refs);
        return vo;
    }

    /**
     * 关联构造:顺序对齐 + 标题行逐字校验。
     * 对齐候选 = 有正文 且 标题行能被 splitter 识别 的节点(B 方案:空父标题不进候选,不产生空关联)。
     */
    private List<OutlineChunkRefEntity> buildRefs(Long fileId,
                                                 List<OutlineNodeEntity> nodes,
                                                 String markdown,
                                                 List<ChunkEntity> chunks) {
        List<OutlineNodeEntity> aligned = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            OutlineNodeEntity node = nodes.get(i);
            if (!hasBody(nodes, i, markdown)) {
                continue;
            }
            if (!SPLITTER_HEADING.matcher(headingLineOf(markdown, node)).matches()) {
                continue;
            }
            aligned.add(node);
        }

        List<OutlineChunkRefEntity> refs = new ArrayList<>();
        OutlineNodeEntity current = null;
        int cursor = 0;
        boolean alignmentBroken = false;
        for (ChunkEntity chunk : chunks) {
            String firstLine = firstLineOf(chunk.getContent());
            if (!SPLITTER_HEADING.matcher(firstLine).matches()) {
                continue; // 无标题前导正文块,无归属节点
            }
            if (alignmentBroken) {
                continue;
            }
            String currentHeading = current == null ? null : headingLineOf(markdown, current).trim();
            if (firstLine.equals(currentHeading)) {
                // 同节续块:splitter 给同节每个 chunk 都前置标题,重复即同节
                if (cursor < aligned.size() && firstLine.equals(headingLineOf(markdown, aligned.get(cursor)).trim())) {
                    alignmentBroken = true;
                    log.warn("Outline 关联降级:fileId={} chunkIndex={} 标题[{}]与下一个待对齐节点同名,无法区分同节续块与同名新节,后续不再关联",
                            fileId, chunk.getChunkIndex(), firstLine);
                    continue;
                }
            } else if (cursor < aligned.size() && firstLine.equals(headingLineOf(markdown, aligned.get(cursor)).trim())) {
                current = aligned.get(cursor);
                cursor++;
            } else {
                alignmentBroken = true;
                log.warn("Outline 关联降级:fileId={} chunkIndex={} 首行[{}]与待对齐节点标题[{}]不等,后续 chunk 不再关联",
                        fileId, chunk.getChunkIndex(), firstLine,
                        cursor < aligned.size() ? headingLineOf(markdown, aligned.get(cursor)).trim() : "<无剩余节点>");
                continue;
            }
            OutlineChunkRefEntity ref = new OutlineChunkRefEntity();
            ref.setNodeId(current.getId());
            ref.setChunkId(chunk.getId());
            ref.setChunkIndex(chunk.getChunkIndex());
            ref.setCreateTime(LocalDateTime.now());
            refs.add(ref);
        }

        if (!alignmentBroken && cursor < aligned.size()) {
            log.warn("Outline 关联不完整:fileId={} 有正文节点 {} 个,实际关联 {} 个", fileId, aligned.size(), cursor);
        }
        return refs;
    }

    /** 节点原文区间内是否存在非空行(= splitter 会为该节产出 chunk 的判据) */
    private boolean hasBody(List<OutlineNodeEntity> nodes, int index, String markdown) {
        int from = nodes.get(index).getSourceEndOffset();
        int to = index + 1 < nodes.size() ? nodes.get(index + 1).getSourceStartOffset() : markdown.length();
        if (from >= to) {
            return false;
        }
        for (String line : markdown.substring(from, to).split("\r\n|\n|\r", -1)) {
            if (!line.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String headingLineOf(String markdown, OutlineNodeEntity node) {
        return markdown.substring(node.getSourceStartOffset(), node.getSourceEndOffset());
    }

    private String firstLineOf(String content) {
        if (content == null) {
            return "";
        }
        int lineBreak = content.indexOf('\n');
        String line = lineBreak < 0 ? content : content.substring(0, lineBreak);
        return line.trim();
    }

    private String preview(String content) {
        if (content == null || content.length() <= PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_LENGTH) + "...";
    }

    private FileEntity requireFile(Long fileId) {
        FileEntity file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        return file;
    }

    private boolean isMarkdown(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }

    /** 越权防护:口径必须与 FileServiceImpl.verifyOwnership 保持一致(knowledge.author == 当前登录用户名) */
    private void verifyOwnership(Long knowledgeId) {
        KnowledgeEntity knowledge = knowledgeMapper.selectById(knowledgeId);
        UserEntity user = UserContext.get();
        if (knowledge == null) {
            throw new BusinessException("知识不存在");
        }
        if (user == null || user.getUsername() == null
                || !user.getUsername().equals(knowledge.getAuthor())) {
            throw new BusinessException("权限不足");
        }
    }
}