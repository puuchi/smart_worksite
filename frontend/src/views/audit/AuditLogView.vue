<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import AppTable from '../../components/common/AppTable.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import { fetchAuditLogs } from '../../api/audit';
import { useProjectStore } from '../../stores/project';
import type { AuditLog } from '../../api/types';

const projectStore = useProjectStore();
const loading = ref(false);
const rows = ref<AuditLog[]>([]);
const pager = reactive({ pageNo: 1, pageSize: 10, total: 0, module: '', action: '' });
const projectId = computed(() => projectStore.currentProject?.projectId || 0);
async function loadRows() { loading.value = true; try { const page = await fetchAuditLogs({ projectId: projectId.value, pageNo: pager.pageNo, pageSize: pager.pageSize, module: pager.module || undefined, action: pager.action || undefined }); rows.value = page.records; pager.total = page.total; } finally { loading.value = false; } }
onMounted(async () => { if (!projectStore.currentProject) await projectStore.fetchProjects(); await loadRows(); });
</script>

<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">审计日志</h2><p class="page-desc">用户操作、外部调用、关键业务动作留痕。</p></div></div>
    <el-card class="work-card"><template #header><div class="filters"><strong>日志列表</strong><div><el-input v-model="pager.module" clearable placeholder="模块" style="width:140px" /><el-input v-model="pager.action" clearable placeholder="动作" style="width:160px;margin-left:8px" /><el-button type="primary" style="margin-left:8px" @click="loadRows">查询</el-button></div></div></template>
      <AppTable :loading="loading" :data="rows" :total="pager.total" :page-no="pager.pageNo" :page-size="pager.pageSize" :columns="[{prop:'createdAt',label:'时间',width:190},{prop:'operatorName',label:'操作人',width:120},{prop:'module',label:'模块',width:120},{prop:'action',label:'动作'},{prop:'targetType',label:'对象类型'},{prop:'targetId',label:'对象ID',width:100},{prop:'result',label:'结果',slot:'result',width:110},{prop:'ip',label:'IP',width:130}]" @page-change="(p,s)=>{pager.pageNo=p;pager.pageSize=s;loadRows()}">
        <template #empty><EmptyState description="暂无审计日志" /></template><template #result="{ row }"><StatusTag :status="row.result" /></template>
      </AppTable>
    </el-card>
  </div>
</template>
<style scoped>.filters{display:flex;align-items:center;justify-content:space-between;}</style>
