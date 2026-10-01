package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 管理端只读聚合查询。
 * 全部为 SELECT:管理端 MVP 不含写操作,不提供任何 DELETE/UPDATE 入口。
 */
@Mapper
public interface AdminMapper {

    /** 全站计数:用户/知识条目/文件/切片(一条 SQL 取四个 COUNT,避免拆成四次往返) */
    Map<String, Object> selectGlobalCounts();

    /** 全站文件处理状态分布(GROUP BY status) */
    List<Map<String, Object>> selectFileStatusDistribution();

    /** 全站 Token 用量汇总(chat/embedding 拆开 + 今日/累计) */
    Map<String, Object> selectGlobalTokenSummary();

    /** 用户列表分页(2026-10 管理端 MVP: 只读,不返回 password) */
    List<AdminUserVO> selectUsers(@Param("keyword") String keyword,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 与分页同条件的总数(count 与 list 必须同一 where 条件,否则分页器会错位) */
    long countUsers(@Param("keyword") String keyword);
}