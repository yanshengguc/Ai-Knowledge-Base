#!/usr/bin/env bash
# ============================================================
# 本地开发启动(显式 local profile,加载 application-local.properties)
# 用法:bash scripts/run-dev.sh
# 注意:不要用裸 spring-boot:run——现在 application.properties 不 include local,
#       裸启动会因缺少 key/密码连不上 MySQL
# ============================================================
set -e
cd "$(dirname "$0")/.."

MH="C:/Users/yansheng/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0"

echo ">>> 本地开发启动(local profile)..."
java -classpath "$MH/boot/plexus-classworlds-2.11.0.jar" \
  -Dclassworlds.conf="$MH/bin/m2.conf" \
  -Dmaven.home="$MH" \
  -Dmaven.multiModuleProjectDirectory="$(pwd)" \
  org.codehaus.plexus.classworlds.launcher.Launcher spring-boot:run -Dspring-boot.run.profiles=local
