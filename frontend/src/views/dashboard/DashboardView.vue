<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatusTag from '../../components/common/StatusTag.vue';
import TaskProgress from '../../components/common/TaskProgress.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import { useProjectStore } from '../../stores/project';
import { fetchReports } from '../../api/report';
import { fetchProjectStatistics } from '../../api/project';
import type { ProjectStatistics, ReportItem } from '../../api/types';

const projectStore = useProjectStore();
const loading = ref(false);
const error = ref('');
const reports = ref<ReportItem[]>([]);
const statistics = ref<ProjectStatistics | null>(null);
const tasks = computed(() => reports.value.map((item) => ({ name: item.reportName, status: item.status, progress: item.progress })));
const metrics = computed(() => [
  { label: '知识库数量', value: statistics.value?.knowledgeBaseCount ?? 0 },
  { label: '待处理任务', value: tasks.value.filter((item) => item.status === 'PROCESSING').length },
  { label: '报告总数', value: statistics.value?.reportCount ?? 0 },
  { label: 'OCR记录', value: statistics.value?.ocrCount ?? 0 }
]);
const primaryActions = [
  { title: '问知识 / 查数据', desc: '向项目资料、政策资讯或业务数据库提问，系统自动判断回答来源。', path: '/qa', cta: '开始提问', accent: 'blue' },
  { title: '审文档', desc: '上传方案、合同或制度文件，按审查标准定位问题并给出修改建议。', path: '/review', cta: '发起审查', accent: 'teal' },
  { title: '生成报告', desc: '选择报告类型、模板和数据来源，生成 Word 或 PDF 报告。', path: '/report', cta: '新建报告', accent: 'orange' },
  { title: '识别材料', desc: '识别身份证、车牌、发票、合同或自定义字段，输出结构化 JSON。', path: '/ocr', cta: '去识别', accent: 'slate' }
];
const supportLinks = [
  { title: '上传项目知识', desc: '让问答可以检索本项目资料', path: '/knowledge' },
  { title: '配置业务数据源', desc: '支撑数据库问答和报告填充', path: '/datasources' },
  { title: '维护政策来源', desc: '演示互联网政策资讯采集入口', path: '/policy' },
  { title: '维护审查/报告模板', desc: '统一管理业务模板', path: '/templates' },
  { title: '查看长任务', desc: '跟踪生成、识别、入库进度', path: '/tasks' }
];

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    if (!projectStore.currentProject) await projectStore.fetchProjects();
    const projectId = projectStore.currentProject?.projectId;
    if (!projectId) throw new Error('请先选择项目');
    const [projectStatistics, page] = await Promise.all([
      fetchProjectStatistics(projectId),
      fetchReports({ projectId, pageNo: 1, pageSize: 5 })
    ]);
    statistics.value = projectStatistics;
    reports.value = page.records;
  } catch (err) {
    error.value = err instanceof Error ? err.message : '首页数据加载失败';
    statistics.value = null;
    reports.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <div class="page" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon />
    <div class="hero-card">
      <div>
        <p class="eyebrow">当前项目</p>
        <h2 class="page-title">{{ projectStore.currentProject?.name || projectStore.currentProject?.projectName || '请选择项目' }}</h2>
        <p class="page-desc">选择你要完成的工作：提问、审查、生成报告或识别材料。</p>
      </div>
      <el-button type="primary" size="large" @click="$router.push('/qa')">直接提问</el-button>
    </div>
    <div class="action-grid">
      <el-card v-for="item in primaryActions" :key="item.title" class="action-card" :class="`action-${item.accent}`" shadow="never" @click="$router.push(item.path)">
        <div class="action-badge">{{ item.title.slice(0, 1) }}</div>
        <h3>{{ item.title }}</h3>
        <p>{{ item.desc }}</p>
        <el-button type="primary" link>{{ item.cta }} →</el-button>
      </el-card>
    </div>
    <div v-if="!loading && !error && !reports.length"><EmptyState description="暂无最近任务，可先从上方入口开始操作。" action-text="刷新" @action="loadData" /></div>
    <template v-else>
      <div class="card-grid"><el-card v-for="item in metrics" :key="item.label" class="work-card"><div class="muted">{{ item.label }}</div><div class="metric">{{ item.value }}</div></el-card></div>
      <div class="two-col">
        <el-card class="work-card">
          <h3 class="panel-title">支撑配置</h3>
          <div class="support-list">
            <button v-for="item in supportLinks" :key="item.title" class="support-item" type="button" @click="$router.push(item.path)">
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </button>
          </div>
        </el-card>
        <el-card class="work-card">
          <h3 class="panel-title">最近任务</h3>
          <EmptyState v-if="!tasks.length" description="暂无任务" />
          <div v-for="task in tasks" :key="task.name" style="margin-bottom:16px">
            <div style="display:flex;justify-content:space-between;margin-bottom:8px"><span>{{ task.name }}</span><StatusTag :status="task.status" /></div>
            <TaskProgress :percentage="task.progress" :status="task.status" />
          </div>
        </el-card>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
  border: 1px solid rgba(30, 94, 255, 0.14);
  border-radius: 18px;
  background:
    radial-gradient(circle at 88% 20%, rgba(15, 118, 110, 0.16), transparent 28%),
    linear-gradient(135deg, #fff 0%, #eff6ff 68%, #ecfeff 100%);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.06);
}
.eyebrow { margin: 0 0 8px; color: var(--sw-teal); font-size: 13px; font-weight: 800; letter-spacing: 0.12em; }
.action-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.action-card { position: relative; min-height: 190px; cursor: pointer; overflow: hidden; transition: transform 0.18s ease, box-shadow 0.18s ease; }
.action-card:hover { transform: translateY(-3px); box-shadow: 0 16px 34px rgba(15, 23, 42, 0.09); }
.action-card h3 { margin: 18px 0 10px; font-size: 19px; }
.action-card p { min-height: 66px; margin: 0 0 12px; color: var(--sw-muted); line-height: 1.65; }
.action-badge { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 14px; color: #fff; font-weight: 800; }
.action-blue .action-badge { background: var(--sw-primary); }
.action-teal .action-badge { background: var(--sw-teal); }
.action-orange .action-badge { background: var(--sw-orange); }
.action-slate .action-badge { background: #475569; }
.support-list { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.support-item { text-align: left; padding: 14px; border: 1px solid var(--sw-border); border-radius: 12px; background: #fff; cursor: pointer; }
.support-item:hover { border-color: var(--sw-primary); background: #f8fbff; }
.support-item strong, .support-item span { display: block; }
.support-item span { margin-top: 6px; color: var(--sw-muted); font-size: 13px; }
@media (max-width: 1200px) {
  .action-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .hero-card { flex-direction: column; align-items: flex-start; }
  .action-grid, .support-list { grid-template-columns: 1fr; }
}
</style>
