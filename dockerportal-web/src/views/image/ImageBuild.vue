<template>
  <div class="image-build">
    <el-card shadow="never">
      <template #header><span>构建镜像</span></template>
      <el-form :model="form" label-width="80px" style="max-width: 600px">
        <el-form-item label="镜像名称" required>
          <el-input v-model="form.imageName" placeholder="myapp:latest" />
        </el-form-item>
        <el-form-item label="Dockerfile">
          <el-input v-model="form.dockerfile" type="textarea" :rows="12"
            placeholder="FROM alpine:latest&#10;RUN echo 'Hello'&#10;CMD ['sh']" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="build" :loading="building">开始构建</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const form = ref({ imageName: '', dockerfile: '' })
const building = ref(false)

async function build() {
  if (!form.value.imageName) {
    ElMessage.warning('请填写镜像名称')
    return
  }
  building.value = true
  try {
    // TODO: implement build API
    ElMessage.info('镜像构建功能开发中')
  } catch (e) {
    ElMessage.error('构建失败')
  } finally {
    building.value = false
  }
}
</script>
