#!/usr/bin/env bash
# ============================================================
# AKB 深度 E2E 验证(真实调用 LLM/DashScope/DashVector,烧钱+慢)
# 用法:bash scripts/test-e2e.sh   —— 发布前/周末跑一次即可
# 包含:评估集(Eval)、ReAct 循环、长记忆、检索质量、重排、工具扩展
# ============================================================
set -e
cd "$(dirname "$0")/.."

MH="C:/Users/yansheng/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0"

echo ">>> AKB 深度 E2E 开始: $(date '+%H:%M:%S')"
# 显式选择 e2e 类(-Dtest),排除 integration(-DexcludedGroups 非空覆盖 pom)
java -classpath "$MH/boot/plexus-classworlds-2.11.0.jar" \
  -Dclassworlds.conf="$MH/bin/m2.conf" \
  -Dmaven.home="$MH" \
  -Dmaven.multiModuleProjectDirectory="$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher \
  -Dtest='EvalHarnessTest,ManualReActVerifyTest,LongTermMemoryVerifyTest,LongTermMemoryGovernanceTest,RetrievalQualityEvalTest,RerankSmokeTest,ToolExtensionVerifyTest' \
  -DexcludedGroups=integration test 2>&1 \
  | grep -aE "Tests run:|BUILD|ERROR" | tail -20

echo ">>> 完成: $(date '+%H:%M:%S')"
