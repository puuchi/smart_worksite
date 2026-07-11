<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppUpload from '../../components/common/AppUpload.vue';
import AppTable from '../../components/common/AppTable.vue';
import JsonViewer from '../../components/common/JsonViewer.vue';
import TaskProgress from '../../components/common/TaskProgress.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import { fetchReviewRecord, fetchReviewTemplates, submitReviewRecord } from '../../api/review';
import { fetchTaskStages } from '../../api/task';
import { useProjectStore } from '../../stores/project';
import type { ID, ReviewRecord, ReviewTemplate, TaskStageLog } from '../../api/types';

const router = useRouter();
const projectStore = useProjectStore();
const loading = ref(false);
const submitting = ref(false);
const templateError = ref('');
const submitError = ref('');
const resultNotice = ref('');
const stageNotice = ref('');
const templates = ref<ReviewTemplate[]>([]);
const selectedTemplateId = ref<ID>('');
const file = ref<File | null>(null);
const currentRecord = ref<ReviewRecord | null>(null);
const submittedInfo = ref<{ recordId?: ID; taskId?: ID; status?: string } | null>(null);
const logs = ref<TaskStageLog[]>([]);
const canSubmit = computed(() => Boolean(templates.value.length && selectedTemplateId.value && file.value && !submitting.value));

function t(text: string) { return text; }
function goTemplates() { router.push('/templates'); }
function progressOf(record: ReviewRecord) { return record.status === 'SUCCESS' || record.status === 'FAILED' ? 100 : 60; }

async function loadTemplates() {
  loading.value = true;
  templateError.value = '';
  try {
    if (!projectStore.currentProject) await projectStore.fetchProjects();
    const projectId = projectStore.currentProject?.projectId;
    templates.value = projectId ? await fetchReviewTemplates(projectId) : [];
    if (!selectedTemplateId.value && templates.value[0]) selectedTemplateId.value = templates.value[0].templateId;
  } catch {
    templateError.value = t('???????????????????');
  } finally {
    loading.value = false;
  }
}

async function loadStages(taskId?: ID) {
  stageNotice.value = '';
  if (!taskId) {
    logs.value = [];
    return;
  }
  try {
    logs.value = await fetchTaskStages(taskId);
  } catch {
    logs.value = [];
    stageNotice.value = t('????????');
  }
}

async function loadRecord(recordId: ID, taskId?: ID, status?: string) {
  resultNotice.value = '';
  submittedInfo.value = { recordId, taskId, status };
  try {
    currentRecord.value = await fetchReviewRecord(recordId);
    submittedInfo.value = { recordId: currentRecord.value.recordId, taskId: currentRecord.value.taskId, status: currentRecord.value.status };
    await loadStages(currentRecord.value.taskId || taskId);
  } catch {
    currentRecord.value = null;
    await loadStages(taskId);
    resultNotice.value = t('???????????????????????????????');
  }
}

async function submit() {
  submitError.value = '';
  if (!templates.value.length) return ElMessage.warning(t('?????????????????????????'));
  if (!selectedTemplateId.value) return ElMessage.warning(t('???????'));
  if (!file.value) return ElMessage.warning(t('????????'));
  const projectId = projectStore.currentProject?.projectId;
  if (!projectId) return ElMessage.warning(t('??????'));
  submitting.value = true;
  resultNotice.value = '';
  stageNotice.value = '';
  try {
    const result = await submitReviewRecord({ projectId, templateId: selectedTemplateId.value, file: file.value });
    submittedInfo.value = result;
    ElMessage.success(t('???????'));
    await loadRecord(result.recordId, result.taskId, result.status);
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : t('?????????????????');
  } finally {
    submitting.value = false;
  }
}

onMounted(loadTemplates);
</script>

<template>
  <div class="page" v-loading="loading">
    <el-alert v-if="templateError" :title="templateError" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-alert v-if="submitError" :title="submitError" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-alert v-if="resultNotice" :title="resultNotice" type="info" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-alert v-if="stageNotice" :title="stageNotice" type="warning" show-icon :closable="false" style="margin-bottom: 12px" />

    <div class="page-header">
      <div>
        <h2 class="page-title">{{ t('????') }}</h2>
        <p class="page-desc">{{ t('??????????????????????? JSON ???') }}</p>
      </div>
      <el-tooltip :content="t('?????????????')"><el-button disabled>{{ t('??????') }}</el-button></el-tooltip>
    </div>

    <el-card class="work-card">
      <el-empty v-if="!loading && !templates.length" :description="t('?????????????????????????')"><el-button type="primary" @click="goTemplates">{{ t('?????') }}</el-button></el-empty>
      <template v-else>
        <el-form inline>
          <el-form-item :label="t('????')"><el-select v-model="selectedTemplateId" style="width: 260px" :placeholder="t('?????')"><el-option v-for="item in templates" :key="item.templateId" :label="item.templateName" :value="item.templateId" /></el-select></el-form-item>
          <el-form-item><el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">{{ t('????') }}</el-button></el-form-item>
        </el-form>
        <AppUpload accept=".doc,.docx,.pdf" :uploading="submitting" @change="file = $event[0] || null" />
      </template>
    </el-card>

    <el-card v-if="submittedInfo && !currentRecord" class="work-card"><h3 class="panel-title">{{ t('?????') }}</h3><p>recordId: {{ submittedInfo.recordId || '-' }}</p><p>taskId: {{ submittedInfo.taskId || '-' }}</p><p>status: {{ submittedInfo.status || '-' }}</p></el-card>

    <EmptyState v-if="!loading && !resultNotice && !currentRecord && !submittedInfo" :description="t('??????????????????')" />
    <template v-else-if="currentRecord">
      <el-card class="work-card"><h3 class="panel-title">{{ t('????') }}</h3><TaskProgress :percentage="progressOf(currentRecord)" :status="currentRecord.status" :logs="logs" /></el-card>
      <div class="two-col">
        <el-card class="work-card">
          <h3 class="panel-title">{{ t('????') }}</h3>
          <AppTable :data="currentRecord.issues || []" :columns="[{ prop: 'severity', label: t('????') }, { prop: 'location', label: t('????') }, { prop: 'ruleName', label: t('????') }, { prop: 'description', label: t('????') }, { prop: 'suggestion', label: t('????') }]">
            <template #empty><EmptyState :description="t('???????')" /></template>
            <el-table-column :label="t('??')" width="110"><template #default><StatusTag :status="currentRecord?.status" /></template></el-table-column>
          </AppTable>
        </el-card>
        <JsonViewer :value="currentRecord" :title="t('?? JSON ??')" />
      </div>
    </template>
  </div>
</template>
