package com.yansheng.aiknowledgebase.service.splitter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
public class SimpleTextSplitter implements DocumentSplitter {

    private final int chunkSize;
    private final int overlap;


    public SimpleTextSplitter() {
        this(500, 100);
    }


    public SimpleTextSplitter(int chunkSize, int overlap) {

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