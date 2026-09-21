<template>
  <GraphPanel />
</template>

<script setup lang="ts">
// B-107 L3: /graph 独立路由保留为薄壳(直输 URL/旧入口可达),实际图体抽至 GraphPanel
// 与 /tree 页第二视图共用(9/21 PO 拍板:图谱结合知识树,树页顶部树形/网图切换)
import GraphPanel from './GraphPanel.vue'
</script>
