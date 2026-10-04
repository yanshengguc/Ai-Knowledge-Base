package com.yansheng.aiknowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * B-110 知识跳转定位请求:把「选中文本」当 query,可选 topK 控制返回条数。
 * 只读端点,不落库、不改任何既有契约。
 */
@Setter
@Getter
public class RetrievalLocateDTO {
    /** 选中文本(去空白后为空则拒绝;超长护栏 2000 字,防检索资源放大) */
    @NotBlank(message = "查询文本不能为空")
    @Size(max = 2000, message = "查询文本过长，最多 2000 字")
    private String query;
    /** 期望返回条数;null→默认 5,非空收敛到 [1,10] */
    private Integer topK;
}
