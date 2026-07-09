<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppTable from '../../components/common/AppTable.vue';
import AppUpload from '../../components/common/AppUpload.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import { createFileParse, deleteFile, downloadByFileId, fetchFileParseContent, fetchFileParseRecords, fetchFiles, retryFileParse, uploadFile, type FileParseRecord } from '../../api/file';
import { useProjectStore } from '../../stores/project';
import type { FileObject } from '../../api/types';

const projectStore = useProjectStore();
const loading = ref(false);
const uploading = ref(false);
const error = ref('');
const parseError = ref('');
const parsingId = ref<string | number>('');
const retryingId = ref<string | number>('');
const deletingId = ref<string | number>('');
const files = ref<FileObject[]>([]);
const parses = ref<FileParseRecord[]>([]);
const selected = ref<FileObject | null>(null);
const uploadFiles = ref<File[]>([]);
const contentVisible = ref(false);
const parsedContent = ref('');
const query = reactive({ pageNo: 1, pageSize: 10, total: 0, keyword: '', bizType: 'KNOWLEDGE_DOC' });
const projectId = computed(() => projectStore.currentProject?.projectId || 0);
const bizTypeOptions = [
  { label: '知识文档', value: 'KNOWLEDGE_DOC' },
  { label: '审查文档', value: 'REVIEW_DOC' },
  { label: '报告模板', value: 'REPORT_TEMPLATE' },
  { label: '审查模板', value: 'REVIEW_TEMPLATE' },
  { label: 'OCR输入', value: 'OCR_INPUT' },
  { label: '报告结果', value: 'REPORT_OUTPUT' }
];

async function loadFiles() {
  if (!projectId.value) {
    files.value = [];
    query.total = 0;
    error.value = '请先选择项目';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const page = await fetchFiles({ pageNo: query.pageNo, pageSize: query.pageSize, keyword: query.keyword, projectId: projectId.value, bizType: query.bizType });
    files.value = page.records;
    query.total = page.total;
  } catch (err) {
    files.value = [];
    query.total = 0;
    error.value = err instanceof Error ? err.message : '文件列表加载失败，请检查后端文件接口';
  } finally { loading.value = false; }
}

async function submitUpload() {
  if (!projectId.value) return ElMessage.warning('请先选择项目');
  if (!query.bizType) return ElMessage.warning('请选择业务类型');
  if (!uploadFiles.value.length) return ElMessage.warning('请先选择文件');
  uploading.value = true;
  try {
    for (const file of uploadFiles.value) await uploadFile(projectId.value, file, query.bizType);
    ElMessage.success('上传完成');
    uploadFiles.value = [];
    await loadFiles();
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`文件上传失败，请检查后端文件存储配置或服务日志。${detail}`);
  } finally { uploading.value = false; }
}

function onBizTypeChange() {
  query.pageNo = 1;
  loadFiles();
}

async function selectFile(row: FileObject) {
  selected.value = row;
  parseError.value = '';
  try { parses.value = await fetchFileParseRecords(row.fileId, row.projectId); }
  catch {
    parses.value = [];
    parseError.value = '解析记录查询失败，请检查后端解析服务配置。';
  }
}

async function parseFile(row: FileObject) {
  if (!row.fileId || !row.projectId) return ElMessage.warning('缺少文件或项目编号，无法提交解析任务');
  parsingId.value = row.fileId;
  try {
    await createFileParse(row.fileId, { projectId: row.projectId, targetFormat: 'MARKDOWN' });
    ElMessage.success('解析任务已提交');
    await selectFile(row);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`解析任务提交失败，请检查后端解析服务配置。${detail}`);
  } finally {
    parsingId.value = '';
  }
}

async function showContent(record: FileParseRecord) {
  try {
    const result = await fetchFileParseContent(record.recordId);
    parsedContent.value = result.content;
    contentVisible.value = true;
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`解析内容查询失败，请检查后端解析服务配置。${detail}`);
  }
}

async function downloadFile(row: FileObject) {
  try {
    await downloadByFileId(row.fileId, row.originalName);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`文件下载失败，请检查后端文件访问 URL 接口。${detail}`);
  }
}

async function removeFile(row: FileObject) {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.originalName}？`, '删除文件', { type: 'warning' });
    deletingId.value = row.fileId;
    await deleteFile(row.fileId);
    ElMessage.success('删除成功');
    await loadFiles();
  } catch (err) {
    if (err === 'cancel' || err === 'close') return;
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`文件删除失败，请检查后端文件接口。${detail}`);
  } finally {
    deletingId.value = '';
  }
}

async function retryParse(record: FileParseRecord) {
  if (!record.recordId) return ElMessage.warning('缺少解析记录编号，无法重试');
  retryingId.value = record.recordId;
  try {
    await retryFileParse(record.recordId);
    ElMessage.success('解析任务已重新提交');
    if (selected.value) await selectFile(selected.value);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`解析任务提交失败，请检查后端解析服务配置。${detail}`);
  } finally {
    retryingId.value = '';
  }
}

onMounted(async () => { if (!projectStore.currentProject) await projectStore.fetchProjects(); await loadFiles(); });
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">文件管理</h2><p class="page-desc">统一资料上传、下载、预览、解析任务与内容查看。</p></div></div>
    <el-card class="work-card">
      <template #header><strong>上传资料</strong></template>
      <el-radio-group v-model="query.bizType" style="margin-bottom:12px" @change="onBizTypeChange">
        <el-radio-button v-for="item in bizTypeOptions" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
      </el-radio-group>
      <AppUpload accept=".doc,.docx,.pdf,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.txt,.md" :uploading="uploading" @change="uploadFiles = $event" />
      <el-button type="primary" :loading="uploading" style="margin-top:12px" @click="submitUpload">开始上传</el-button>
    </el-card>
    <el-card class="work-card">
      <template #header><div class="table-head"><strong>文件列表</strong><el-input v-model="query.keyword" clearable placeholder="搜索文件名" style="width:220px" @keyup.enter="loadFiles" /></div></template>
      <AppTable :loading="loading" :error="error" :data="files" :total="query.total" :page-no="query.pageNo" :page-size="query.pageSize" :columns="[{prop:'originalName',label:'文件名'},{prop:'fileType',label:'类型',width:100},{prop:'size',label:'大小',width:110},{prop:'status',label:'状态',slot:'status',width:110}]" @page-change="(p,s)=>{query.pageNo=p;query.pageSize=s;loadFiles()}">
        <template #empty><EmptyState description="暂无文件，可先上传项目资料。" /></template>
        <template #status="{ row }"><StatusTag :status="row.status" /></template>
        <el-table-column label="操作" width="280"><template #default="{ row }"><el-button link type="primary" @click="selectFile(row)">解析记录</el-button><el-button link :loading="parsingId === row.fileId" @click="parseFile(row)">解析</el-button><el-button link @click="downloadFile(row)">下载</el-button><el-button link type="danger" :loading="deletingId === row.fileId" @click="removeFile(row)">删除</el-button></template></el-table-column>
      </AppTable>
    </el-card>
    <el-card class="work-card">
      <template #header><strong>{{ selected?.originalName || '解析记录' }}</strong></template>
      <el-alert v-if="parseError" :title="parseError" type="warning" show-icon :closable="false" style="margin-bottom: 12px" />
      <AppTable :data="parses" :columns="[{prop:'recordId',label:'记录ID',width:100},{prop:'parseType',label:'解析类型'},{prop:'status',label:'状态',slot:'status'},{prop:'progress',label:'进度'}]">
        <template #empty><EmptyState description="选择文件后查看解析记录" /></template>
        <template #status="{ row }"><StatusTag :status="row.status" /></template>
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="showContent(row)">内容</el-button><el-button link :loading="retryingId === row.recordId" @click="retryParse(row)">重试解析</el-button></template></el-table-column>
      </AppTable>
    </el-card>
    <el-dialog v-model="contentVisible" title="解析内容" width="760px"><pre class="content-box">{{ parsedContent }}</pre></el-dialog>
  </div>
</template>

<style scoped>
.table-head { display:flex; justify-content:space-between; align-items:center; }
.content-box { white-space:pre-wrap; max-height:60vh; overflow:auto; background:#f8fafc; padding:14px; border-radius:10px; }
</style>
