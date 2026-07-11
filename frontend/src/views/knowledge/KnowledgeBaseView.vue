<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import AppUpload from '../../components/common/AppUpload.vue';
import AppTable from '../../components/common/AppTable.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import { createKnowledgeBase, fetchKnowledgeBases, fetchKnowledgeDocuments, triggerDocumentIndex, uploadKnowledgeDocument } from '../../api/knowledge';
import { useProjectStore } from '../../stores/project';
import type { ID, KnowledgeBase, KnowledgeDocument } from '../../api/types';

const projectStore = useProjectStore();
const loading = ref(false);
const docsLoading = ref(false);
const creating = ref(false);
const uploading = ref(false);
const error = ref('');
const docsError = ref('');
const bases = ref<KnowledgeBase[]>([]);
const docs = ref<KnowledgeDocument[]>([]);
const activeBaseId = ref<ID>('');
const dialogVisible = ref(false);
const selectedFiles = ref<File[]>([]);
const indexingId = ref<ID>('');
const form = reactive({ name: '', description: '' });
const activeBase = computed(() => bases.value.find((item) => String(item.knowledgeBaseId) === String(activeBaseId.value)) || null);

async function loadBases(selectId?: ID) {
  loading.value = true;
  error.value = '';
  try {
    if (!projectStore.currentProject) await projectStore.fetchProjects();
    const projectId = projectStore.currentProject?.projectId;
    if (!projectId) {
      bases.value = [];
      docs.value = [];
      activeBaseId.value = '';
      return;
    }
    bases.value = await fetchKnowledgeBases(projectId);
    const nextId = selectId || activeBaseId.value;
    const matched = bases.value.find((item) => String(item.knowledgeBaseId) === String(nextId));
    activeBaseId.value = matched ? matched.knowledgeBaseId : (bases.value[0]?.knowledgeBaseId || '');
    if (activeBaseId.value) await loadDocs(activeBaseId.value);
    else docs.value = [];
  } catch (err) {
    error.value = err instanceof Error ? err.message : '?????????????????????';
  } finally {
    loading.value = false;
  }
}

async function loadDocs(baseId: ID) {
  docsLoading.value = true;
  docsError.value = '';
  try {
    docs.value = (await fetchKnowledgeDocuments(baseId)).records;
  } catch (err) {
    docsError.value = err instanceof Error ? err.message : '??????????????????????';
    docs.value = [];
  } finally {
    docsLoading.value = false;
  }
}

async function submitCreate() {
  if (!form.name.trim()) return ElMessage.warning('????????');
  const projectId = projectStore.currentProject?.projectId;
  if (!projectId) return ElMessage.warning('??????');
  creating.value = true;
  error.value = '';
  try {
    const created = await createKnowledgeBase(projectId, { name: form.name.trim(), description: form.description.trim() });
    dialogVisible.value = false;
    form.name = '';
    form.description = '';
    ElMessage.success('???????');
    await loadBases(created.knowledgeBaseId);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`???????????????????${detail}`);
  } finally {
    creating.value = false;
  }
}

async function uploadDocs() {
  if (!activeBaseId.value) return ElMessage.warning('???????');
  if (!selectedFiles.value.length) return ElMessage.warning('??????');
  uploading.value = true;
  docsError.value = '';
  try {
    for (const file of selectedFiles.value) await uploadKnowledgeDocument(activeBaseId.value, file);
    ElMessage.success('??????');
    selectedFiles.value = [];
    await loadDocs(activeBaseId.value);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`??????????????????????????????${detail}`);
  } finally {
    uploading.value = false;
  }
}

async function handleIndex(row: KnowledgeDocument) {
  const documentId = row.documentId;
  if (!documentId) return ElMessage.warning('??ID?????????');
  indexingId.value = documentId;
  docsError.value = '';
  try {
    await triggerDocumentIndex(documentId);
    ElMessage.success('???????');
    await loadDocs(activeBaseId.value);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`??????????????????????${detail}`);
  } finally {
    indexingId.value = '';
  }
}

watch(activeBaseId, (id) => { if (id) loadDocs(id); });
onMounted(loadBases);
</script>

<template>
  <div class="page" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="page-header">
      <div>
        <h2 class="page-title">?????</h2>
        <p class="page-desc">???????????????????</p>
      </div>
      <el-button type="primary" @click="dialogVisible = true">?????</el-button>
    </div>

    <EmptyState v-if="!loading && !bases.length" description="??????????????" action-text="?????" @action="dialogVisible = true" />
    <template v-else>
      <el-card class="work-card">
        <div class="base-list">
          <button v-for="base in bases" :key="base.knowledgeBaseId" type="button" class="base-card" :class="{ active: String(activeBaseId) === String(base.knowledgeBaseId) }" @click="activeBaseId = base.knowledgeBaseId">
            <strong>{{ base.name }}</strong>
            <span>{{ base.description || '????' }}</span>
            <small>???{{ base.domain || '-' }}</small>
          </button>
        </div>
        <p v-if="activeBase" class="muted">??????{{ activeBase.name }} / <StatusTag :status="activeBase.status" /></p>
      </el-card>

      <el-card class="work-card">
        <h3 class="panel-title">????</h3>
        <AppUpload accept=".doc,.docx,.pdf,.xls,.xlsx,.ppt,.pptx,.jpg,.jpeg,.png,.txt,.md" :uploading="uploading" @change="selectedFiles = $event" />
        <el-button type="primary" style="margin-top: 12px" :loading="uploading" :disabled="!activeBaseId" @click="uploadDocs">????????</el-button>
      </el-card>

      <el-card class="work-card">
        <h3 class="panel-title">??????</h3>
        <el-alert v-if="docsError" :title="docsError" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
        <AppTable :loading="docsLoading" :data="docs" :columns="[{ prop: 'title', label: '????' }, { prop: 'sourceType', label: '????', width: 120 }, { prop: 'indexStatus', label: '????', slot: 'index', width: 110 }, { prop: 'errorMessage', label: '??' }, { prop: 'createdAt', label: '????', width: 180 }]">
          <template #empty><EmptyState description="?????????????????" /></template>
          <template #index="{ row }"><StatusTag :status="row.indexStatus" /></template>
          <el-table-column label="??" width="160"><template #default="{ row }"><el-button link type="primary" :loading="String(indexingId) === String(row.documentId)" @click="handleIndex(row)">????</el-button></template></el-table-column>
        </AppTable>
      </el-card>
    </template>

    <el-dialog v-model="dialogVisible" title="?????" width="520px">
      <el-form label-width="96px">
        <el-form-item label="?????"><el-input v-model="form.name" placeholder="????????" /></el-form-item>
        <el-form-item label="??"><el-input v-model="form.description" type="textarea" placeholder="????????" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">??</el-button><el-button type="primary" :loading="creating" @click="submitCreate">??</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.base-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.base-card { text-align: left; border: 1px solid var(--sw-border); border-radius: 12px; background: #fff; padding: 14px; cursor: pointer; display: grid; gap: 8px; color: inherit; }
.base-card.active { border-color: var(--sw-primary); box-shadow: 0 0 0 3px rgba(30, 94, 255, 0.12); }
.base-card span, .base-card small, .muted { color: var(--sw-muted); }
.panel-title { margin: 0 0 12px; font-size: 16px; }
</style>
