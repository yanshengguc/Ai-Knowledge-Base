package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.dto.UserRegisterDTO;
import com.yansheng.aiknowledgebase.vo.UserVO;

public interface UserService {
    UserVO getUserById(long id);
    void register(UserRegisterDTO dto);
}
