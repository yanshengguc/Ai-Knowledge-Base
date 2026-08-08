package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.service.splitter.SimpleTextSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

public class SimpleTextSplitterTest {


    @Test
    public void testSplit(){

        SimpleTextSplitter splitter =
                new SimpleTextSplitter(5,2);


        String text = "ABCDEFGHIJK";


        List<String> result = splitter.split(text);


        result.forEach(System.out::println);
    }
}
