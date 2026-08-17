package com.yansheng.aiknowledgebase.service.impl;

import com.yansheng.aiknowledgebase.exception.BusinessException;
import com.yansheng.aiknowledgebase.dto.LoginDTO;
import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.entity.UserEntity;
import com.yansheng.aiknowledgebase.mapper.UserMapper;
import com.yansheng.aiknowledgebase.service.UserService;
import com.yansheng.aiknowledgebase.utils.JwtUtil;
import com.yansheng.aiknowledgebase.vo.UserVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    private static final int MAX_LOGIN_FAIL = 5;
    private static final long LOCK_MINUTES = 10;

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserServiceImpl(UserMapper userMapper, JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
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
user.setPassword(passwordEncoder.encode(dto.getPassword()));  // BCrypt 哈希后入库
user.setNickname(dto.getNickname());
userMapper.insert(user);

    }

    @Override
    public String login(LoginDTO dto) {
        // 登录防刷:同一用户名失败 5 次,锁定 10 分钟(Redis 计数)
        String failKey = "login:fail:" + dto.getUsername();
        Object failCount = redisTemplate.opsForValue().get(failKey);
        if (failCount != null && ((Number) failCount).intValue() >= MAX_LOGIN_FAIL) {
            throw new BusinessException("尝试次数过多,请" + LOCK_MINUTES + "分钟后再试");
        }

        UserEntity userEntity = userMapper.getUserByName(dto.getUsername());
        if (userEntity == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getPassword(), userEntity.getPassword())) {
            Long cnt = redisTemplate.opsForValue().increment(failKey);
            if (cnt != null && cnt == 1L) {
                redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
            }
            throw new BusinessException("密码错误");
        }
        // 登录成功,清除失败计数
        redisTemplate.delete(failKey);

        String token = jwtUtil.generateToken(
                userEntity.getId(),
                userEntity.getUsername()
        );
        return token;
    }


}
