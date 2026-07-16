package com.yansheng.aiknowledgebase.mapper;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface UserMapper {
    UserEntity findByid(long id);


}
