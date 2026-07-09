<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppTable from '../../components/common/AppTable.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import JsonViewer from '../../components/common/JsonViewer.vue';
import { createDataSource, fetchDataSources, queryDataSource, testDataSource } from '../../api/datasource';
import { useProjectStore } from '../../stores/project';
import type { DataSourceItem, DataSourceQueryResult } from '../../api/types';

const projectStore = useProjectStore();
const loading = ref(false);
const saving = ref(false);
const rows = ref<DataSourceItem[]>([]);
const dialogVisible = ref(false);
const result = ref<DataSourceQueryResult | null>(null);
const selectedIds = ref<Array<string | number>>([]);
const question = ref('本月未闭环安全问题有多少？');
const form = reactive({ name: '', type: 'MYSQL', host: '', port: 3306, databaseName: '', username: '', password: '', description: '' });
const projectId = computed(() => projectStore.currentProject?.projectId || 0);
async function loadRows() { loading.value = true; try { rows.value = (await fetchDataSources({ projectId: projectId.value, pageNo: 1, pageSize: 100 })).records; } finally { loading.value = false; } }
async function save() { saving.value = true; try { await createDataSource({ projectId: projectId.value, ...form }); ElMessage.success('已保存数据源'); dialogVisible.value = false; await loadRows(); } finally { saving.value = false; } }
async function test(row: DataSourceItem) { const res = await testDataSource(row.dataSourceId); ElMessage[res.success ? 'success' : 'error'](res.message); }
async function ask() { result.value = await queryDataSource({ projectId: projectId.value, question: question.value, dataSourceIds: selectedIds.value }); }
onMounted(async () => { if (!projectStore.currentProject) await projectStore.fetchProjects(); await loadRows(); });
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">数据源问答</h2><p class="page-desc">配置业务数据库，后续接口上线后可直接启用自然语言查数。</p></div><el-button type="primary" @click="dialogVisible=true">新增数据源</el-button></div>
    <el-card class="work-card"><template #header><strong>数据源列表</strong></template>
      <AppTable :loading="loading" :data="rows" :columns="[{prop:'name',label:'名称'},{prop:'type',label:'类型',width:110},{prop:'host',label:'主机'},{prop:'databaseName',label:'数据库'},{prop:'status',label:'状态',slot:'status'}]">
        <template #empty><EmptyState description="暂无数据源" /></template><template #status="{ row }"><StatusTag :status="row.status" /></template>
        <el-table-column type="selection" width="44" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="test(row)">测试</el-button></template></el-table-column>
      </AppTable>
    </el-card>
    <el-card class="work-card"><template #header><strong>数据库问答</strong></template>
      <el-input v-model="question" type="textarea" :rows="3" placeholder="请输入业务数据问题" />
      <el-button type="primary" style="margin-top:12px" @click="ask">生成查询</el-button>
      <div v-if="result" class="result"><el-alert v-if="result.summary" :title="result.summary" type="success" show-icon /><pre v-if="result.sql">{{ result.sql }}</pre><JsonViewer :value="result.rows" /></div>
    </el-card>
    <el-dialog v-model="dialogVisible" title="数据源配置" width="560px"><el-form label-width="90px"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item><el-form-item label="类型"><el-select v-model="form.type"><el-option label="MySQL" value="MYSQL" /><el-option label="PostgreSQL" value="POSTGRESQL" /><el-option label="SQL Server" value="SQLSERVER" /></el-select></el-form-item><el-form-item label="主机"><el-input v-model="form.host" /></el-form-item><el-form-item label="端口"><el-input-number v-model="form.port" /></el-form-item><el-form-item label="数据库"><el-input v-model="form.databaseName" /></el-form-item><el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item><el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>.result { margin-top:14px; display:flex; flex-direction:column; gap:12px; } pre { background:#0f172a; color:#d1fae5; padding:12px; border-radius:10px; overflow:auto; }</style>
