<template>
  <div class="settings-view">
    <el-card shadow="never">
      <template #header><span>系统设置</span></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="系统名称">DockerPortal</el-descriptions-item>
        <el-descriptions-item label="版本">1.0.0</el-descriptions-item>
        <el-descriptions-item label="后端框架">Spring Boot 3.2 + JDK 21</el-descriptions-item>
        <el-descriptions-item label="前端框架">Vue 3 + Element Plus</el-descriptions-item>
        <el-descriptions-item label="数据库">H2 (内嵌)</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
</script>
