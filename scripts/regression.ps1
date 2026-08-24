# 一键全量回归: 所有测试(单元 + e2e, 仅排除 integration)
# 用法:  powershell -File scripts\regression.ps1            (全量, 约 6-8 分钟)
#        powershell -File scripts\regression.ps1 -Fast      (仅单元层, 约 1 分钟)
# 判读:  末尾打印 PASS/FAIL 大字 + 汇总行; 退出码 0=全过, 1=有失败(可直接接 CI)
param([switch]$Fast)

$log = Join-Path $env:TEMP ("regression-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".log")

Write-Host "== 全量回归开始 $(Get-Date -Format 'HH:mm:ss') | 日志: $log ==" -ForegroundColor Cyan

$args_ = @("test")
if ($Fast) {
    # Fast: 排除 integration,e2e(不调 LLM/向量库, 秒级反馈)
    $args_ += "-DexcludedGroups=integration,e2e"
} else {
    # 全量: 仅排除 integration(e2e 照跑, 含真实 LLM/Redis/MySQL/DashVector)
    $args_ += "-DexcludedGroups=integration"
}

& mvn @args_ *> $log
$mvnExit = $LASTEXITCODE

# 汇总行: 取最后的 "Tests run: N, Failures: F, Errors: E, Skipped: S"(全局行)
$summary = Select-String -Path $log -Pattern "Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+\s*$" |
    Select-Object -Last 1 -ExpandProperty Line
$failed  = Select-String -Path $log -Pattern "^\[ERROR\]\s+\w+.*:(\d+)|<<< FAILURE!|<<< ERROR!" |
    Select-Object -ExpandProperty Line -First 20

Write-Host ""
Write-Host "================= 回归结果 =================" -ForegroundColor Yellow
if ($summary) { Write-Host ("汇总: " + $summary.Trim()) }
if ($mvnExit -eq 0 -and $summary -and $summary -match "Failures: 0, Errors: 0") {
    Write-Host "  [PASS] 全部测试通过" -ForegroundColor Green
    exit 0
} else {
    Write-Host "  [FAIL] 存在失败用例,明细:" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host ("    " + $_.Trim()) }
    Write-Host "完整日志: $log" -ForegroundColor Gray
    exit 1
}
