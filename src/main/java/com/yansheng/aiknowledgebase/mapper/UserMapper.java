package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import org.apache.catalina.User;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface UserMapper {
    UserEntity findById(long id);
UserEntity getUserByName(String username);
int insert(UserEntity userEntity);
UserEntity getUserByPassword(String password);
}
