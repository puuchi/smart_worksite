<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppTable from '../../components/common/AppTable.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import TaskProgress from '../../components/common/TaskProgress.vue';
import { cancelTask, fetchTaskDetail, fetchTaskStages, retryTask } from '../../api/task';
import type { TaskDetail, TaskStageLog } from '../../api/types';

const loading = ref(false);
const task = ref<TaskDetail | null>(null);
const logs = ref<TaskStageLog[]>([]);
const form = reactive({ taskId: '' });
const retrying = ref(false);
const canceling = ref(false);
const retryableStatuses = new Set(['FAILED']);
const cancelableStatuses = new Set(['PENDING', 'QUEUED', 'RUNNING', 'RETRYING']);
const canRetry = computed(() => Boolean(task.value && retryableStatuses.has(normalizeStatus(task.value.status)) && (task.value.retryCount || 0) < (task.value.maxRetryCount || 0)));
const canCancel = computed(() => Boolean(task.value && cancelableStatuses.has(normalizeStatus(task.value.status))));

function normalizeStatus(status?: string) {
  return (status || '').toUpperCase();
}

async function loadTask() { if (!form.taskId) return; loading.value = true; try { task.value = await fetchTaskDetail(form.taskId); logs.value = await fetchTaskStages(form.taskId); } finally { loading.value = false; } }
async function retry() {
  if (!task.value) return;
  if (!canRetry.value) return ElMessage.warning(`当前任务状态为 ${task.value.status}，不能重试`);
  retrying.value = true;
  try { await retryTask(task.value.taskId); ElMessage.success('任务已重新入队'); await loadTask(); }
  finally { retrying.value = false; }
}
async function cancel() {
  if (!task.value) return;
  if (!canCancel.value) return ElMessage.warning(`当前任务状态为 ${task.value.status}，不能取消`);
  canceling.value = true;
  try { await cancelTask(task.value.taskId); ElMessage.success('取消请求已提交'); await loadTask(); }
  finally { canceling.value = false; }
}
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">任务中心</h2><p class="page-desc">报告生成、OCR、知识入库、文件解析等长任务状态和阶段日志。</p></div></div>
    <el-card class="work-card"><el-input v-model="form.taskId" placeholder="输入 taskId 查询" style="max-width:320px" @keyup.enter="loadTask"><template #append><el-button :loading="loading" @click="loadTask">查询</el-button></template></el-input></el-card>
    <el-card v-if="task" class="work-card"><template #header><div class="table-head"><strong>{{ task.taskType }} #{{ task.taskId }}</strong><div><el-button :loading="retrying" :disabled="!canRetry" @click="retry">重试</el-button><el-button type="warning" :loading="canceling" :disabled="!canCancel" @click="cancel">取消</el-button></div></div></template><StatusTag :status="task.status" /><TaskProgress :percentage="task.progress || 0" :status="task.status" :logs="logs" /></el-card>
    <el-card class="work-card"><template #header><strong>阶段日志</strong></template><AppTable :loading="loading" :data="logs" :columns="[{prop:'stageName',label:'阶段'},{prop:'status',label:'状态',slot:'status'},{prop:'message',label:'说明'},{prop:'createdAt',label:'时间'}]"><template #empty><EmptyState description="输入任务ID查看阶段日志" /></template><template #status="{ row }"><StatusTag :status="row.status" /></template></AppTable></el-card>
  </div>
</template>
<style scoped>.table-head{display:flex;align-items:center;justify-content:space-between;}</style>
