package com.yansheng.aiknowledgebase.config;

import java.util.ArrayList;
import java.util.List;

public class SecurityConfig {
    public static final List<String> WHITE_LIST = List.of("/api/user/login"
    , "/api/user/register");
}
