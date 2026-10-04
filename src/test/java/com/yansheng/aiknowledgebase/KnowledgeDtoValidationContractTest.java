package com.yansheng.aiknowledgebase;

import com.yansheng.aiknowledgebase.controller.KnowledgeController;
import com.yansheng.aiknowledgebase.handler.GlobalExceptionHandler;
import com.yansheng.aiknowledgebase.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-127 DTO 参数校验前移:@Valid + jakarta 约束在 Web 层拦截非法请求。
 * 契约:HTTP 恒为 200,校验失败 body code=500(不退化为 Spring 默认 400)。
 * 承重证据:非法请求下 verify(service, never()) 证明未进入 Service(校验在 Web 层生效)。
 */
class KnowledgeDtoValidationContractTest {

    private KnowledgeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(KnowledgeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addKnowledgeMissingTitleRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("标题不能为空"));
        verify(service, never()).addKnowledge(any());
    }

    @Test
    void addKnowledgeBlankTitleRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).addKnowledge(any());
    }

    @Test
    void addKnowledgeMissingContentRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).addKnowledge(any());
    }

    @Test
    void addKnowledgeTooLongTitleRejectedBeforeService() throws Exception {
        String longTitle = "a".repeat(201);
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).addKnowledge(any());
    }

    @Test
    void addKnowledgeTooLongCategoryRejectedBeforeService() throws Exception {
        String longCategory = "a".repeat(51);
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"category\":\"" + longCategory + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).addKnowledge(any());
    }

    @Test
    void addKnowledgeValidPayloadReachesService() throws Exception {
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).addKnowledge(any());
    }

    @Test
    void addKnowledgeTitleAtMaxLengthAccepted() throws Exception {
        String maxTitle = "a".repeat(200);
        mvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + maxTitle + "\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).addKnowledge(any());
    }

    @Test
    void updateKnowledgeMissingTitleRejectedBeforeService() throws Exception {
        mvc.perform(put("/api/knowledge/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).updateKnowledge(any(), any());
    }

    @Test
    void updateKnowledgeTooLongCategoryRejectedBeforeService() throws Exception {
        String longCategory = "a".repeat(51);
        mvc.perform(put("/api/knowledge/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"category\":\"" + longCategory + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).updateKnowledge(any(), any());
    }

    @Test
    void createNoteMissingTitleRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/knowledge/1/note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).createNote(any(), any(), any(), any());
    }

    @Test
    void createNoteMissingContentRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/knowledge/1/note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).createNote(any(), any(), any(), any());
    }

    @Test
    void createNoteTooLongTitleRejectedBeforeService() throws Exception {
        String tooLongTitle = "a".repeat(256);
        mvc.perform(post("/api/knowledge/1/note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tooLongTitle + "\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        verify(service, never()).createNote(any(), any(), any(), any());
    }

    @Test
    void createNoteValidPayloadReachesService() throws Exception {
        mvc.perform(post("/api/knowledge/1/note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).createNote(any(), any(), any(), any());
    }
}
