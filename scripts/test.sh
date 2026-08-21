#!/usr/bin/env bash
# ============================================================
# AKB 一键回归测试(单元 + 本地集成,免费快速,不含 e2e)
# 用法:bash scripts/test.sh
# 看结果:输出 "Tests run: N, Failures: 0, Errors: 0" 且 BUILD SUCCESS = 绿
#         任何 Failures/Errors 非 0 = 红,必须修
# 深度验证(真实 LLM,烧钱):bash scripts/test-e2e.sh
# ============================================================
set -e
cd "$(dirname "$0")/.."

MH="C:/Users/yansheng/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0"

echo ">>> AKB 一键回归开始: $(date '+%H:%M:%S')"
java -classpath "$MH/boot/plexus-classworlds-2.11.0.jar" \
  -Dclassworlds.conf="$MH/bin/m2.conf" \
  -Dmaven.home="$MH" \
  -Dmaven.multiModuleProjectDirectory="$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher test 2>&1 \
  | grep -aE "Tests run:|BUILD|ERROR" | tail -20

echo ">>> 完成: $(date '+%H:%M:%S')"
echo ">>> 判定:看上方 BUILD SUCCESS 且 Tests run Failures/Errors = 0 即为绿"
