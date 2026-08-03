package com.yansheng.aiknowledgebase.service.splitter;

import java.util.List;

public interface DocumentSplitter {
    List<String> split(String text);
}
