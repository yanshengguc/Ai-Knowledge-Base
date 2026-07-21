package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.common.BusinessException;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
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
        UserEntity userEntity=userMapper.findById(id);
        if(userEntity==null){
            throw new BusinessException("用户不存在");
        }
        UserVO userVO=new UserVO();
userVO.setUsername(userEntity.getUsername());

        return userVO;
    }

    @Override
    public void register(UserRegisterDTO dto) {
UserEntity userEntity=userMapper.getUserByName(dto.getUsername());
if(userEntity!=null){
    throw new BusinessException("用户已经存在");
}
UserEntity  user=new UserEntity();
user.setUsername(dto.getUsername());
user.setPassword(dto.getPassword());
user.setNickname(dto.getNickname());
userMapper.insert(user);

    }

}
