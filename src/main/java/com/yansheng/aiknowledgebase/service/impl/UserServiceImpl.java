package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.UserService;
import com.yansheng.aiknowledgebase.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserVO getUserById(long id) {
        userMapper.findByid(id);
        UserEntity userEntity=userMapper.findByid(id);
        UserVO userVO=new UserVO();
userVO.setUsername(userEntity.getUsername());

        return userVO;
    }

}
