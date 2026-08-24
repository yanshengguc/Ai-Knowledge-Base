package com.yansheng.aiknowledgebase.service.splitter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * 固定窗口切片(chunkSize 窗口 + overlap 重叠)。
 * splitter.mode=simple 时启用;默认走 StructureAwareSplitter(结构感知)。
 */
@Component
@ConditionalOnProperty(name = "splitter.mode", havingValue = "simple")
public class SimpleTextSplitter implements DocumentSplitter {

    private final int chunkSize;
    private final int overlap;

    /**
     * Spring 构造注入:切片参数来自配置(splitter.chunk-size / splitter.overlap),
     * 与 retrieval.top-k 等检索参数同一套配置治理方式。
     */
    public SimpleTextSplitter(
            @Value("${splitter.chunk-size:500}") int chunkSize,
            @Value("${splitter.overlap:100}") int overlap) {

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize必须大于0");
        }

        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap参数非法");
        }

        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }


    @Override
    public List<String> split(String text) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(start + chunkSize, text.length());

            String chunk = text.substring(start, end);

            chunks.add(chunk);
if (end == text.length()) {
    break;
}
            start += chunkSize - overlap;
        }

        return chunks;
    }
}