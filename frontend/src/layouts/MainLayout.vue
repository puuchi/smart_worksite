<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { ChatLineRound, DocumentChecked, Files, Folder, FolderOpened, House, Notebook, Picture, Setting, SwitchButton, Tickets, User, UserFilled } from '@element-plus/icons-vue';
import { useProjectStore } from '../stores/project';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();
const userStore = useUserStore();

const menus = [
  { path: '/dashboard', title: '?????', icon: House, permission: 'dashboard:view' },
  { path: '/projects', title: '?????', icon: FolderOpened, permission: 'project:view' },
  { path: '/files', title: '????', icon: Files, permission: 'file:view' },
  { path: '/templates', title: '????', icon: Tickets, permission: 'template:view' },
  { path: '/knowledge', title: '?????', icon: Notebook, permission: 'knowledge:view' },
  { path: '/qa', title: '????', icon: ChatLineRound, permission: 'qa:view' },
  { path: '/review', title: '????', icon: DocumentChecked, permission: 'review:view' },
  { path: '/report', title: '????', icon: Files, permission: 'report:view' },
  { path: '/ocr', title: 'OCR??', icon: Picture, permission: 'ocr:view' },
  { path: '/project/manage', title: '????', icon: Folder, permission: 'project:manage' },
  { path: '/project/members', title: '????', icon: UserFilled, permission: 'project:member:manage' },
  { path: '/system/users', title: '????', icon: User, permission: 'system:user:manage' },
  { path: '/system/roles', title: '????', icon: Setting, permission: 'system:user:manage' }
];

const visibleMenus = computed(() => menus.filter((item) => userStore.hasPermission(item.permission)));
const activeMenu = computed(() => route.path.startsWith('/report') ? '/report' : route.path);
const currentProject = computed(() => projectStore.currentProject);

onMounted(async () => {
  if (!userStore.user && userStore.token) await userStore.fetchCurrentUser();
  if (!projectStore.projects.length) await projectStore.fetchProjects();
});

async function logout() {
  await ElMessageBox.confirm('?????????', '????', { type: 'warning' });
  await userStore.logout();
  router.replace('/login');
}
</script>

<template>
  <el-container class="main-layout">
    <el-aside width="236px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">AI</div>
        <div><strong>????</strong><span>???????</span></div>
      </div>
      <el-menu :default-active="activeMenu" router class="side-menu">
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar" height="64px">
        <div>
          <div class="current-project">?????{{ currentProject?.name || currentProject?.projectName || '????' }}</div>
          <div class="project-meta">{{ currentProject?.code || currentProject?.projectCode || '-' }} / {{ currentProject?.address || currentProject?.location || '-' }}</div>
        </div>
        <div class="top-actions">
          <el-select v-model="projectStore.currentProjectId" style="width: 240px" :loading="projectStore.loading" @change="projectStore.switchProject">
            <el-option v-for="project in projectStore.projects" :key="project.projectId" :label="project.name || project.projectName" :value="String(project.projectId)" :disabled="!['ACTIVE', 'ENABLED'].includes(project.status)" />
          </el-select>
          <el-dropdown>
            <span class="user-chip">{{ userStore.displayName }} / {{ userStore.roles[0] || '????' }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="SwitchButton" @click="logout">????</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="content"><router-view :key="projectStore.currentProjectId" /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-layout { min-height: 100vh; background: var(--sw-bg); }
.sidebar { background: linear-gradient(180deg, #0f2f63 0%, #133a74 58%, #0f766e 135%); color: #fff; }
.brand { height: 64px; display: flex; align-items: center; gap: 12px; padding: 0 18px; border-bottom: 1px solid rgba(255,255,255,0.12); }
.brand-mark { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; background: linear-gradient(135deg, #1e5eff, #0f766e); font-weight: 800; }
.brand span { display: block; margin-top: 3px; font-size: 12px; color: rgba(255,255,255,0.72); }
.side-menu { border-right: 0; background: transparent; }
.side-menu :deep(.el-menu-item) { color: rgba(255,255,255,0.82); margin: 6px 10px; border-radius: 10px; }
.side-menu :deep(.el-menu-item.is-active) { background: rgba(255,255,255,0.14); color: #fff; }
.topbar { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid var(--sw-border); }
.current-project { font-weight: 700; }
.project-meta { margin-top: 4px; color: var(--sw-muted); font-size: 12px; }
.top-actions { display: flex; align-items: center; gap: 14px; }
.user-chip { cursor: pointer; padding: 8px 12px; border: 1px solid var(--sw-border); border-radius: 999px; }
.content { padding: 20px; }
</style>
