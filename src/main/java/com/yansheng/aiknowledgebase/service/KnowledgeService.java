package com.yansheng.aiknowledgebase.service;

import com.yansheng.aiknowledgebase.dto.KnowledgeAddDTO;
import com.yansheng.aiknowledgebase.dto.KnowledgeUpdateDTO;
import com.yansheng.aiknowledgebase.vo.KnowledgeDetailVO;
import com.yansheng.aiknowledgebase.vo.KnowledgePageVO;
import com.yansheng.aiknowledgebase.vo.KnowledgeVO;

import java.util.List;

public interface KnowledgeService {
    List<KnowledgeVO> getKnowledgeList();

    /**
     * B-104 服务端分页:按当前用户 + 关键词(title/content) + 分类过滤,返回一页。
     * 分页护栏与 admin/users 一致:page 下限 1,size 默认 10 / 上限 50。
     */
    KnowledgePageVO getKnowledgePage(int page, int size, String keyword, String category);

 KnowledgeDetailVO getKnowledgeById(Long id) throws InterruptedException;
  void addKnowledge(KnowledgeAddDTO dto);
  void  updateKnowledge(Long id, KnowledgeUpdateDTO dto);
  void deleteKnowledge(Long id);

    /**
     * 写优先:在知识条目下新建 Markdown 笔记。
     * 笔记 = 特殊文件(不入 OSS),内容同步切片+向量化,立刻可被检索。
     * source:笔记来源(如 "ai-chat"),null 表示用户手写,来源随 fileType 透出供前端标记。
     */
    void createNote(Long knowledgeId, String title, String content, String source);

    /**
     * 一键导出当前用户全部知识 + 文件/笔记清单为 Markdown(数据主权:用户可随时带走自己的数据)。
     */
    String exportMarkdown();
}
