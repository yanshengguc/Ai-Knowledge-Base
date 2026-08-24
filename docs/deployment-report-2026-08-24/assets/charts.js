(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  var baseText = { color: muted, fontSize: 12 };

  // --- Chart 1: 订单费用构成 ---
  var costChart = echarts.init(document.getElementById('chart-cost'), null, { renderer: 'svg' });
  costChart.setOption({
    animation: false,
    grid: { left: 8, right: 30, top: 30, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', appendToBody: true, valueFormatter: function (v) { return '¥ ' + Number(v).toFixed(2); } },
    xAxis: {
      type: 'category',
      data: ['订单原价', '限时折扣', '代金券抵扣', '现金支出'],
      axisLabel: { color: ink, fontSize: 12.5 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '元',
      nameTextStyle: baseText,
      axisLabel: baseText,
      splitLine: { lineStyle: { color: rule, type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      barWidth: 46,
      data: [
        { value: 709.73, itemStyle: { color: muted } },
        { value: 424.75, itemStyle: { color: accent2 } },
        { value: 284.98, itemStyle: { color: accent2 } },
        { value: 0, itemStyle: { color: accent } }
      ],
      label: {
        show: true,
        position: 'top',
        color: ink,
        fontWeight: 600,
        fontSize: 12.5,
        formatter: function (p) { return p.value === 0 ? '¥ 0' : (p.dataIndex === 0 ? '¥709.73' : '-¥' + p.value.toFixed(2)); }
      },
      markLine: {
        silent: true,
        symbol: 'none',
        data: [{ yAxis: 0 }],
        lineStyle: { color: rule }
      }
    }]
  });

  // --- Chart 2: 内存占用分布（实测） ---
  var memChart = echarts.init(document.getElementById('chart-mem'), null, { renderer: 'svg' });
  memChart.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true, formatter: '{b}<br/>{c} MB（{d}%）' },
    legend: { bottom: 0, textStyle: baseText, itemWidth: 10, itemHeight: 10 },
    series: [{
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: bg2, borderWidth: 2 },
      label: { show: true, color: ink, fontSize: 12, formatter: '{b}\n{c} MB' },
      data: [
        { value: 549, name: 'aikb（Spring Boot + JVM）', itemStyle: { color: accent } },
        { value: 205, name: 'MySQL / Redis / Nginx', itemStyle: { color: accent2 } },
        { value: 106, name: '系统基础', itemStyle: { color: muted } },
        { value: 1086, name: '可用余量', itemStyle: { color: rule } }
      ]
    }]
  });

  // --- Chart 3: 内存预算 vs 实测 ---
  var budgetChart = echarts.init(document.getElementById('chart-budget'), null, { renderer: 'svg' });
  budgetChart.setOption({
    animation: false,
    grid: { left: 8, right: 20, top: 34, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', appendToBody: true, valueFormatter: function (v) { return v + ' MB'; } },
    legend: { top: 0, textStyle: baseText, itemWidth: 12, itemHeight: 8 },
    xAxis: {
      type: 'value',
      axisLabel: baseText,
      splitLine: { lineStyle: { color: rule, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: ['系统基础', 'Nginx', 'Redis', 'MySQL 8', 'Spring Boot (JVM)'],
      axisLabel: { color: ink, fontSize: 12 },
      axisLine: { lineStyle: { color: rule } },
      axisTick: { show: false }
    },
    series: [
      {
        name: '部署前预算',
        type: 'bar',
        barWidth: 12,
        itemStyle: { color: rule },
        data: [250, 20, 50, 350, 700]
      },
      {
        name: '实测/估算',
        type: 'bar',
        barWidth: 12,
        itemStyle: { color: accent },
        label: { show: true, position: 'right', color: muted, fontSize: 11 },
        data: [106, 10, 15, 180, 549]
      }
    ]
  });

  window.addEventListener('resize', function () {
    costChart.resize();
    memChart.resize();
    budgetChart.resize();
  });
})();
