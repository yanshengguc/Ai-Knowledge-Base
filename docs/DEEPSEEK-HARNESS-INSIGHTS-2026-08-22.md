# 借鉴 DeepSeek Harness 的 AKB 评估体系升级(2026-08-22)

> **DeepSeek Harness**(deepseek-ai/DeepSeek-Harness,官方开源):模型推理与评测一体化框架,核心抽象 = **数据集(Dataset)+ 模型(Model)+ 流水线(Pipeline)**,用统一、可复现的方式跑 MATH-500/AIME/GPQA/LiveCodeBench 等基准,并支持 **MCP adapter 评估真实工具调用**。
>
> 我们不搬框架(AKB 是应用不是模型),**只借工程模式**——正好升级你已有的 `EvalHarnessTest`(15 个真实用例,统计工具选择准确率)。

---

## 一、DeepSeek Harness 的核心模式 → AKB 差距映射

| Harness 工程模式 | 它解决什么 | AKB 现状(EvalHarnessTest) | 差距 |
|---|---|---|---|
| **数据集驱动**(benchmark 独立于代码) | 加用例不用改代码,可扩展可共享 | 15 个用例**写死在测试代码里** | 🔴 应抽成 JSON 数据集 |
| **可复现性参数管理**(采样数/温度/max tokens 集中配置) | 换台机器/换个参数分数就变 | 无固定参数声明 | 🟡 应固定并记录 |
| **指标分层统计**(按基准分组:数学/代码/工具) | 知道短板在哪个任务类型 | 只有一个"工具选择准确率"总数 | 🟡 应分任务类型统计 |
| **MCP adapter 评估真实工具调用**(不是合成 mock) | 验证模型真的会调你的工具 | 评估集是真实用例,但未区分"纯工具选择"vs"真实执行" | 🟡 加"真实执行链路"评估集 |
| **分层评测策略**(迭代期便宜模型 / 发布门禁强模型) | 成本-准确率权衡 | 只有默认渠道 | 🟢 多模型对比(你有渠道面板) |
| **失败样本回灌**(eval 失败样本纳入回归) | 修复可验证、防回退 | 无失败用例留痕 | 🟢 失败 case 进错题集/评估集 |

---

## 二、落地建议(按优先级)

### 🟢 P0-A 评估集数据化:15 个用例从代码 → JSON 文件

```
src/test/resources/eval/cases.json,每个 case:
{
  "id": "retrieval_001",
  "task": "retrieval",           // 任务类型:retrieval/refusal/stats/time/websearch/multitool
  "difficulty": "easy",          // easy/medium/hard
  "input": "有没有讲 JVM 调优的资料?",
  "expectTool": "file_search",
  "expectParams": {"query": "JVM 调优"},
  "note": "知识库检索主路径"
}
```
`EvalHarnessTest` 改为**读 JSON 跑循环**——加用例只改数据,不改代码(DeepSeek Harness 的"数据集驱动")。

### 🟢 P0-B 指标分级:一个总数 → 分层报告

```
输出 EVAL 报告(日志/文档):
  总体工具选择准确率: 13/15 = 86.7%
  按任务类型: retrieval 8/8 | refusal 2/2 | stats 1/2 | websearch 2/3
  失败明细: stats_002(选了 file_search,应 knowledge_stats)
```
**一眼看到短板在哪类任务**(Harness 按基准分组的理念)。

### 🟡 P1-C 可复现性:固定采样参数 + 失败样本回灌

- 评估调用固定 `temperature=0`、`maxTokens`、模型名,并在报告头记录("本次评估: deepseek-v4-flash, temp=0, 15 用例")——复现 DeepSeek "换参数分数就变"的教训
- **失败用例自动落库/记录到错题集**:跑完 eval,失败 case 写入 `docs/EVAL-FAILURES.md`(和你的面试错题集一个理念)——下次修复后重跑验证"这次修好了没"

### 🟡 P1-D 真实工具链路评估(呼应 Harness 的 MCP adapter)

```
新增一组"端到端真实执行"评估:模型选对工具后,工具真实执行并返回(不 mock)
  验证:file_search 真的搜到、file_trace 真的返回文件、web_search 真的联网
价值:面试讲"我不只测工具选择,还测真实执行链路 —— 就像 DeepSeek Harness 的 MCP adapter"
```

### 🟢 P2-E 多模型 + 成本对比(你有渠道面板,9 月可选)

```
同一评估集,对比 deepseek-v4-flash vs 其他渠道:
  模型 A: 准确率 86.7%  成本 ¥0.03/次
  模型 B: 准确率 91.2%  成本 ¥0.45/次
决策:迭代期用 A,发布/重要功能用 B —— 分层评测策略(tiered eval)
```

---

## 三、面试叙事(把评估体系讲成亮点)

> 「我的评估集早期就是把 15 个用例写在测试里,后来参考 DeepSeek Harness 的工程模式重构:
> ① **数据集驱动**——用例抽成 JSON,加用例不改代码;
> ② **指标分层**——不只报一个准确率,按检索/拒答/统计分任务统计,一眼看到短板;
> ③ **可复现**——固定 temperature/模型名,报告头记录评估参数;
> ④ **失败样本回灌**——评估失败的用例进错题集,修复后重跑验证。
> 我觉得评估不是跑一次就完,是**和开发形成闭环**。」

---

## 四、诚实边界(不照搬)

```
❌ 不引入 Harness 框架本体(它是给基础模型评测设计的,AKB 是应用)
❌ 不做 MATH/AIME 等通用基准(与你业务无关,面试官也不看)
✅ 只借:数据集驱动 / 指标分层 / 可复现 / 失败回灌 / 分层评测 五个模式
```

---

## 五、落地节奏

| 时间 | 事项 | 来源模式 |
|---|---|---|
| 8/23-26(前端期机动) | P0-A 用例抽 JSON + P0-B 分层指标 | 数据集驱动 + 分层统计 |
| 8/27-29(部署冲刺前) | P1-C 可复现参数 + 失败回灌 | 可复现性 + 闭环 |
| 9 月面试期 | P1-D 真实工具链路 + P2-E 多模型成本对比 | MCP adapter + tiered eval |
