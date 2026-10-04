package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.OutlineChunkRefEntity;
import com.yansheng.aiknowledgebase.entity.OutlineNodeEntity;
import com.yansheng.aiknowledgebase.vo.OutlineSourceChunkVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** B-114 Outline 导航层读写(不回填、不改写 knowledge_chunk) */
@Mapper
public interface OutlineMapper {

    /** 先序批量插入节点(父节点先于子节点);useGeneratedKeys 回填 id */
    int insertNodes(List<OutlineNodeEntity> nodes);

    /** 回填 parent_id(CASE WHEN 单语句,避免 N 次 UPDATE) */
    int updateParents(List<OutlineNodeEntity> nodes);

    /** 批量插入节点↔chunk 关联 */
    int insertRefs(List<OutlineChunkRefEntity> refs);

    /** 幂等入口:按文件删除节点(外键级联删除关联) */
    int deleteNodesByFileId(@Param("fileId") Long fileId);

    List<OutlineNodeEntity> selectNodesByFileId(@Param("fileId") Long fileId);

    OutlineNodeEntity selectNodeById(@Param("id") Long id);

    /** 某文件下全部关联(nodeId + chunkId),计数在服务层完成 */
    List<OutlineChunkRefEntity> selectRefsByFileId(@Param("fileId") Long fileId);

    /** 节点溯源:关联的真实切片(含正文,preview 由服务层截断) */
    List<OutlineSourceChunkVO> selectRefsByNodeId(@Param("nodeId") Long nodeId);

    /**
     * B-114 Phase2 树路由只读查询:在「当前用户文件范围」内,按关键词 LIKE 匹配节点的 title / heading_path。
     * 归属校验链路:knowledge_outline_node.file_id → knowledge_file.knowledge_id → knowledge.user_id = #{userId}。
     * 关键词任一命中即算命中(OR),词由服务层切分并做长度过滤;仅返回节点 id,limit 由调用方控制。
     */
    List<Long> selectNodeIdsByUserKeywords(@Param("userId") Long userId,
                                           @Param("keywords") List<String> keywords,
                                           @Param("limit") int limit);

    /**
     * B-114 Phase2 树路由只读查询:按 nodeIds 批量取关联的 chunkId 集合(去重);调用方保证非空。
     * 归属校验由上游 {@link #selectNodeIdsByUserKeywords} 的 user_id 限定传递保证
     * (node 已属该用户文件范围,其关联 chunk 同属该用户),本查询不再重复 join knowledge。
     */
    List<Long> selectChunkIdsByNodeIds(@Param("nodeIds") List<Long> nodeIds);
}
