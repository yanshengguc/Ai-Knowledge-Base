package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.entity.QueryResponse;

public interface QueryService {
    QueryResponse query(String userQuestion);
}