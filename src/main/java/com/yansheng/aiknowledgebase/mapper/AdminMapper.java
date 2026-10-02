package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.vo.AdminUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 管理端聚合查询与写操作(B-116 起含写)。
 * 读:概览 / 用户分页;写:建号 / 改角色 / 重置密码 / 删除,以及删除前置的归属计数。
 * 注意:所有返回用户信息的查询一律不得 select password。
 */
@Mapper
public interface AdminMapper {

    /** 全站计数:用户/知识条目/文件/切片(一条 SQL 取四个 COUNT,避免拆成四次往返) */
    Map<String, Object> selectGlobalCounts();

    /** 全站文件处理状态分布(GROUP BY status) */
    List<Map<String, Object>> selectFileStatusDistribution();

    /** 全站 Token 用量汇总(chat/embedding 拆开 + 今日/累计) */
    Map<String, Object> selectGlobalTokenSummary();

    /** 用户列表分页(不返回 password) */
    List<AdminUserVO> selectUsers(@Param("keyword") String keyword,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 与分页同条件的总数(count 与 list 必须同一 where 条件,否则分页器会错位) */
    long countUsers(@Param("keyword") String keyword);

    /** 按用户名查角色(不存在返回 null;role NOT NULL,故非空即该用户名已存在) */
    String selectRoleByUsername(@Param("username") String username);

    /** 表中 role='admin' 的用户名列表(生效管理员集合的库内部分) */
    List<String> selectAdminUsernames();

    /** 按 id 查用户(含 role,不含 password;不存在返回 null) */
    AdminUserVO selectUserById(@Param("id") long id);

    /** 新建用户(role 由调用方按白名单值校验后传入) */
    int insertUser(@Param("username") String username,
                   @Param("password") String password,
                   @Param("nickname") String nickname,
                   @Param("role") String role);

    /** 更新角色 */
    int updateRole(@Param("id") long id, @Param("role") String role);

    /** 重置密码(传入已 BCrypt 哈希后的值) */
    int updatePassword(@Param("id") long id, @Param("password") String password);

    /** 删除用户 */
    int deleteUser(@Param("id") long id);

    /** 用户名下知识条目计数(删除前置护栏:防孤儿数据) */
    long countKnowledgeByUserId(@Param("userId") long userId);

    /** 用户名下文件计数(删除前置护栏:防孤儿数据) */
    long countFilesByUserId(@Param("userId") long userId);
}