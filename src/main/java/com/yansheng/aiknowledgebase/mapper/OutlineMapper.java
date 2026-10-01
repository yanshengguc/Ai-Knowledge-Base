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
}