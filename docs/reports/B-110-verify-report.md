# B-110 定位高亮链路 · F1/F2/F4 完整验证报告

> 生成日期：2026-10-04（线上复核补做于 2026-10-04 晚）
> 范围：Sprint 10 · B-110「知识跳转 MVP」真实用户视角走查发现的三项缺陷（F1 中 / F2 低 / F4 线上）
> 结论：**三项全部修复并线上复核通过**；唯一残留未覆盖项 = 物理真机上的原生长按选词（自动化环境限制）
> 关联留痕：`docs/sprint.md`「### 修复留痕（F1 中 / F2 低 · 2026-10-04）」「### 部署上线留痕（2026-10-04 · B-110 F1/F2 修复上线）」「### F4 修复与验证留痕（2026-10-04 · B-110 · 方案 a）」；`docs/backlog.md` TD-006/007/008；`HANDOFF.md` 顶部更新行

## 0. 被测链路

选中文本 → `POST /api/retrieval/locate` → 结果面板（`.locate-item`）→「查看原文」→ `FilePreview.open({locate})` → 归一化匹配 → `<mark class="fp-locate-highlight">`；匹配失败降级 `.fp-locate-banner`。选区浮层由 `useTextSelection`（`mouseup`/`touchend`/`selectionchange`）驱动。

---

## 1. 变更清单（3 项缺陷 → 6 个提交）

| 缺陷 | 修复提交 | 文档留痕提交 | 改动 |
|---|---|---|---|
| F1 | `e6b7ca6` | `2436575` | 后端 `FileServiceImpl.java`（5 文件中的 1 个业务文件） |
| F2 | `e6b7ca6` | `2436575` | 前端 `useTextSelection.ts` +12/-1、`Chat.vue`/`Detail.vue` +2/-2 |
| F4 | `e943817` | `e661140` | 前端 `FilePreview.vue` +16（1 文件） |
| 复核留痕 | — | `445cf34`/`687534d`/`c3b917c`/`cdc92b5`/`1d3174c`/`22b9445` | `docs/sprint.md` + `HANDOFF.md` |

零夹带：无 DDL、无新依赖、无无关改名。

---

## 2. F1（中）· 笔记来源「查看原文」死路 + 误导提示

| 项 | 内容 |
|---|---|
| **现象** | 命中 `source=manual` 手记切片时，点「查看原文」走死路，并弹「该格式暂不支持在线预览(仅支持 md 文本)」，与事实矛盾 |
| **根因** | `FileServiceImpl.getFileContent` 仅按 `.md` 后缀 / OSS 取正文；笔记无 OSS 对象 ⇒ 返回 `null` |
| **修复** | 在 `.md` 判断**之前**新增笔记分支：`fileType` 以 `text/markdown` 开头 **且** `fileUrl` 为空 → 按 `chunkIndex` 升序以 `"\n"` 拼接 chunk 正文（空切片返回 `""`，与 `readNoteContent` 同口径）；`verifyOwnership` 归属校验仍前置 |

**验证（两级承重）**

| 层级 | 实测 | 结果 |
|---|---|---|
| 本地单测 | `mvn -o test -Dtest=FileContentTest` **9/0/0**；全量 **292/0/0** | PASS |
| 本地浏览器 | `/api/file/145/content` `null`→**316 字**（含「空值缓存」）；`146` 仍走 OSS **269**（回归不变）；抽屉渲染笔记正文、`marks=1`、无死路提示 | PASS |
| **线上 HTTP** | `POST /api/retrieval/locate`（demo，query「缓存击穿」）首条命中 `fileId=4` / `缓存三大问题:穿透、击穿、雪崩`；`GET /api/file/4/content` → **200 / code:200 / fileType=text/markdown;source=manual / content 非 null**（含「缓存穿透/缓存击穿/缓存雪崩/小结」全文；修复前为 `null`） | PASS |
| **线上浏览器** | `/chat` 提问 → 选「缓存击穿」→ 定位 → 命中手记切片 →「查看原文」→ 抽屉**正常渲染笔记正文**、`mark.fp-locate-highlight=1`、`.fp-locate-banner=0`、**无**「仅支持 md 文本」 | PASS |

截图：`%TEMP%\trae\screenshots\F1-reverify-final-state.png`

---

## 3. F2（低）· 定位后浮层按钮残留不消失

| 项 | 内容 |
|---|---|
| **现象** | 点击定位浮层按钮后，浮层按钮不消失、持续残留 |
| **根因** | `@mousedown.prevent` + 点击**自身**的 `mouseup` 经 `evaluateDeferred()`（`setTimeout(evaluate,0)`）再次置 `visible=true`；仅 `hide()` 不够，因选区仍在 |
| **修复** | `useTextSelection` 新增并导出 `clearSelection()`（`hide()` + `window.getSelection()?.removeAllRanges()`），`Chat.vue`/`Detail.vue` 的 `onLocate` 改调它；`hide()` 语义不变（仍供 Esc/scroll） |

**验证**

| 层级 | 实测 | 结果 |
|---|---|---|
| 本地浏览器 | 真实 CDP 鼠标点击后 **700ms** `visibleLocateBtn=0`（命中/未命中两路径），控制台错误 0 | PASS |
| **线上浏览器** | 点击前 `.selection-locate-btn` **DOM=1 / 可见=1**；点击后 **1000ms** `floatBtnCount=0`、`floatBtnVisible=0`（已从 DOM 移除）；定位结果面板 **4 行**正常 | PASS |

截图：`%TEMP%\trae\screenshots\F2-reverify-final-state.png`

---

## 4. F4（线上缺陷）· 定位高亮失效

| 项 | 内容 |
|---|---|
| **现象** | 线上笔记与 OSS md 两类命中，抽屉均 `marks=0`、降级横幅 `.fp-locate-banner=1`，高亮完全不生效 |
| **根因** | 线上 `POST /api/retrieval/locate` 返回切片 `content` 把换行存成**字面量两字符** `\`+`n`（实测 `has_LITERAL_backslash_n=True, has_real_NL=False`），来源为**向量路 DashVector metadata**；而 `/api/file/{id}/content` 正文是**真实换行**。前端归一化 `SKIP_CHARS` 含真实 `\n` 但**不含反斜杠/字母 n** ⇒ needle 残留 `\`+`n`、haystack 不含 ⇒ `indexOf` 恒 -1 ⇒ 降级、无高亮 |
| **修复（方案 a）** | `FilePreview.vue` 的 `normalizeChunk`(needle) 与 `buildDomIndex`(haystack) **对称**逐字符识别「字面量转义对」`\`+(n\|r\|t) 并**整对跳过 2 字符**（`i+=1; continue`，**不**折叠成空格 ⇒「保留字符↔源偏移」1:1、不错位）；孤立 `\`、真实换行路径不受影响；字面量 `\r\n` 连续两次对跳过 |

> 口径订正：此前本地走查记录「高亮通过」属**本地数据形态侥幸**（本地 seed 切片为真实换行），**不代表线上可用**；本次以「构造线上数据形态」的 A/B 重新承重。

**验证**

| 层级 | 实测 | 结果 |
|---|---|---|
| 独立评审 | `PASS with nits`（无 high/medium）：两处条件完全对称；`chars.length === map.length` 恒成立；边界（末尾孤立 `\`、后接非 n/r/t、字面量 `\r\n`、接续真实换行、emoji 代理对）逐条安全；无夹带 | PASS |
| 独立验证·本地 A/B | 人为构造字面量 `\n` 切片（`UPDATE knowledge_chunk … id=284`）：**修复前 `marks=0 / banner=1`；修复后 `marks=1 / banner=0`** | PASS |
| 构建 | 前端 `npm run build`（vue-tsc+vite）**exit 0**；后端零改动 | PASS |
| **线上浏览器（桌面）** | `/chat` 选「缓存击穿」→ 定位 **4 条** →「查看原文」→ **`mark.fp-locate-highlight=1`、`.fp-locate-banner=0`**；只读接口复核根因链路（切片 content 为字面量 `\n`、MySQL 全库字面量计数 0、file content 为真实换行） | PASS |
| **线上移动端 `touchend`** | 派发真实 `Touch`/`TouchEvent`（`touched_via=TouchEvent`）→ 浮层出现 → 定位 4 条 →「查看原文」→ **`marks=1`、`banner=0`** | PASS（偏差：合成 TouchEvent，非真机） |

截图：`F4-verify-final-state.png`、`F4-touchend-final-state.png`

---

## 5. 移动端触摸手势补充复核（PARTIAL）

方法升级为 **CDP 移动端视口（390×844、`mobile:true`）+ `Input.dispatchTouchEvent` 真实触摸输入**（优于上轮 JS 合成 `TouchEvent`）：

| 子项 | 实测 | 结论 |
|---|---|---|
| 行为1 原生长按选择 | 真实长按（touchStart→0.8s→touchEnd）后 `selection=""`、按钮 **0** | **未通过**（headless 不支持原生长按选词，**仍需真机**）；回退（程序化选区+真实触摸 tap）后 selection=`缓存击穿`、按钮=1 |
| 行为2 触摸点击浮层按钮 | 按钮 **1→0**、`.locate-item` **4** 条 | 通过（真实触摸） |
| 行为3 移动端滚动隐藏浮层 | 真实手指滑动 → 浮层 **1→0**、内层容器 `scrollTop` **2100→2456** | 通过（真实触摸） |

截图：`touch-longpress-state.png`、`touch-longpress-native-failed.png`、`touch-locate-panel.png`、`touch-scroll-hidden.png`；证据 `%TEMP%\aikb-f4\touch_result.json`

---

## 6. 部署与门禁留痕

| 批次 | 后端 jar SHA256 | 前端 index.html SHA256 | 重启 | verify_deploy | 回滚点 |
|---|---|---|---|---|---|
| F1/F2 | `1760f895…b89086`（118,562,057 B） | `d19608d9…a590df` | active 23:11:38 CST | **3/3 PASS** | `app.jar.bak-20261004-pre-b110fix` |
| F4 | `67AF289C…DDB50AEB7`（118,562,057 B） | `61111216…90270327C0` | active 23:43:01 CST | **3/3 PASS** | `app.jar.bak-20261004-pre-f4` |

- 提交前全量回归 **293/0/0** 全绿；双端 SHA256 逐位一致；启动窗口 ERROR/异常 **0**；匿名 `/api/retrieval/locate` → **401**；`/tmp` 与 `.old` 残留已清；凭据仅进程环境变量传入、**未落盘**。

---

## 7. 技术债与残留风险

- **TD-006 / TD-007**：F1/F2 本次修复**关闭**。
- **TD-008（新增·接受）**：归一化把字面量 `\n`/`\r`/`\t` 视同空白 ⇒ 正文若**真含**这两字符（如 `C:\new`、代码块里的 `\n` 示例）时 Range 可能多覆盖 2 字符——属既有子串匹配设计固有；**决定=本轮接受**，**回滚=去掉两处分支**。
- **残留未覆盖**：① 物理真机原生长按选词（含选择手柄）——headless 限制；② 移动端长 query 单路 BM25 命中率（F3，环境相关，非代码缺陷，登记观察）。

---

## 8. 结论

F1/F2/F4 三项缺陷均已 **修复 → 独立评审（PASS，无 high/medium）→ 独立验证（本地 A/B + 线上真实浏览器）→ 部署上线 → 线上复核通过**，满足「收口三件事 + 攻防/走查」要求；`origin/main` 与本地同步、工作区干净。除物理真机长按手势外，全部可自动化项已线上覆盖。

---

## 9. 证据文件清单

| 类别 | 路径 |
|---|---|
| 截图 | `%TEMP%\trae\screenshots\{F1-reverify-final-state.png, F2-reverify-final-state.png, F4-verify-final-state.png, F4-touchend-final-state.png, touch-longpress-state.png, touch-longpress-native-failed.png, touch-locate-panel.png, touch-scroll-hidden.png}` |
| CDP 驱动与中间证据 | `%TEMP%\aikb-f4\{cdp.py, touch_result.json, before_dom.json, fixed_dom.json, before_drawer.png, fixed_drawer.png, step2_locate_response.json, file146_content_raw.json, jwt.txt}` |
