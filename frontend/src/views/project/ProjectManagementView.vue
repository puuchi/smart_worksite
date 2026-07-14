<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppTable from '../../components/common/AppTable.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import EmptyState from '../../components/common/EmptyState.vue';
import { createProject, deleteProject, fetchProjects, updateProject, updateProjectStatus } from '../../api/project';
import * as memberApi from '../../api/member';
import * as userApi from '../../api/user';
import type { ProjectItem, ProjectMemberItem, UserItem } from '../../api/types';
import { useProjectStore } from '../../stores/project';
import { useUserStore } from '../../stores/user';
import { hasSuspiciousText } from '../../utils/textQuality';

const projectStore = useProjectStore();
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const error = ref('');
const projects = ref<ProjectItem[]>([]);
const dialogVisible = ref(false);
const memberDrawerVisible = ref(false);
const memberLoading = ref(false);
const userLoading = ref(false);
const memberSubmitting = ref(false);
const memberError = ref('');
const userError = ref('');
const memberDialogVisible = ref(false);
const editingUserId = ref<number | string | null>(null);
const selectedMemberProject = ref<ProjectItem | null>(null);
const members = ref<ProjectMemberItem[]>([]);
const allUsers = ref<UserItem[]>([]);
const canManageProject = computed(() => userStore.hasPermission('project:manage'));
const canManageMembers = computed(() => userStore.hasPermission('project:member:manage'));
const form = reactive({ projectId: '', name: '', code: '', address: '', description: '' });
const memberForm = ref({ userId: '' as string | number, projectRole: 'BUSINESS_USER' });
const PROJECT_ROLES = [
  { value: 'PROJECT_ADMIN', label: '项目管理员' },
  { value: 'BUSINESS_USER', label: '业务人员' },
  { value: 'VIEWER', label: '只读用户' }
];
const roleLabel = (role: string) => PROJECT_ROLES.find((item) => item.value === role)?.label || role;
const existingUserIds = computed(() => new Set(members.value.map((member) => String(member.userId))));
const availableUsers = computed(() => allUsers.value.filter((user) => user.status === 'ENABLED' && !existingUserIds.value.has(String(user.id))));

async function loadProjects() {
  loading.value = true;
  error.value = '';
  try {
    const page = await fetchProjects({ pageNo: 1, pageSize: 100 });
    projects.value = page.records;
    projectStore.projects = projects.value;
    if (!projects.value.length) {
      projectStore.switchProject('');
      return;
    }
    const current = projects.value.find((item) => String(item.projectId) === String(projectStore.currentProjectId)) || projects.value[0];
    if (!projectStore.currentProjectId || !projects.value.some((item) => String(item.projectId) === String(projectStore.currentProjectId))) {
      projectStore.switchProject(current.projectId);
    }
  } catch (err) {
    projects.value = [];
    error.value = err instanceof Error ? err.message : '项目列表加载失败，请检查后端项目接口。';
  } finally {
    loading.value = false;
  }
}

function switchProject(project: ProjectItem) {
  projectStore.projects = projects.value;
  projectStore.switchProject(project.projectId);
}

async function openMemberManagement(project: ProjectItem) {
  switchProject(project);
  selectedMemberProject.value = project;
  memberDrawerVisible.value = true;
  await Promise.all([fetchMembers(project.projectId), fetchUsers()]);
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

async function fetchMembers(projectId = selectedMemberProject.value?.projectId) {
  memberError.value = '';
  if (!projectId) {
    members.value = [];
    memberError.value = '请先选择项目';
    return;
  }
  memberLoading.value = true;
  try {
    members.value = await memberApi.listMembers(projectId);
  } catch (err) {
    members.value = [];
    memberError.value = getErrorMessage(err, '项目成员加载失败');
  } finally {
    memberLoading.value = false;
  }
}

async function fetchUsers() {
  userError.value = '';
  userLoading.value = true;
  try {
    const res = await userApi.listUsers({ pageNo: 1, pageSize: 100, status: 'ENABLED' });
    allUsers.value = res.records;
  } catch (err) {
    allUsers.value = [];
    userError.value = getErrorMessage(err, '可选用户加载失败');
  } finally {
    userLoading.value = false;
  }
}

async function openAddMember() {
  editingUserId.value = null;
  memberForm.value = { userId: '', projectRole: 'BUSINESS_USER' };
  memberDialogVisible.value = true;
  await Promise.all([fetchMembers(), fetchUsers()]);
}

function openEditMember(row: ProjectMemberItem) {
  editingUserId.value = row.userId;
  memberForm.value = { userId: row.userId, projectRole: row.projectRole };
  memberDialogVisible.value = true;
}

async function submitMember() {
  const projectId = selectedMemberProject.value?.projectId;
  if (!projectId) return ElMessage.error('请先选择项目');
  if (!memberForm.value.userId && editingUserId.value == null) return ElMessage.error('请选择用户');
  memberSubmitting.value = true;
  try {
    if (editingUserId.value == null) {
      await memberApi.addMember(projectId, { userId: memberForm.value.userId, projectRole: memberForm.value.projectRole });
      ElMessage.success('成员添加成功');
    } else {
      await memberApi.updateMember(projectId, editingUserId.value, { userId: editingUserId.value, projectRole: memberForm.value.projectRole });
      ElMessage.success('角色更新成功');
    }
    memberDialogVisible.value = false;
    await fetchMembers(projectId);
  } catch (err) {
    ElMessage.error(getErrorMessage(err, editingUserId.value == null ? '成员添加失败' : '角色更新失败'));
  } finally {
    memberSubmitting.value = false;
  }
}

async function removeMember(row: ProjectMemberItem) {
  const projectId = selectedMemberProject.value?.projectId;
  if (!projectId) return ElMessage.error('请先选择项目');
  try {
    await ElMessageBox.confirm(`确认移除成员“${row.displayName || row.username}”？`, '移除成员', { type: 'warning' });
    await memberApi.removeMember(projectId, row.userId);
    ElMessage.success('已移除');
    await fetchMembers(projectId);
  } catch (err) {
    if (err === 'cancel' || err === 'close') return;
    ElMessage.error(getErrorMessage(err, '移除成员失败'));
  }
}

function openCreate() {
  if (!canManageProject.value) return ElMessage.warning('当前账号没有项目管理权限');
  Object.assign(form, { projectId: '', name: '', code: '', address: '', description: '' });
  dialogVisible.value = true;
}

function openEdit(row: ProjectItem) {
  if (!canManageProject.value) return ElMessage.warning('当前账号没有项目管理权限');
  Object.assign(form, { projectId: String(row.projectId), name: row.name, code: row.code, address: row.address, description: row.description || '' });
  dialogVisible.value = true;
}

async function saveProject() {
  if (!canManageProject.value) return ElMessage.warning('当前账号没有项目管理权限');
  if (!form.name.trim()) return ElMessage.warning('请输入项目名称');
  if (!form.code.trim()) return ElMessage.warning('请输入项目编码');
  saving.value = true;
  try {
    const isCreate = !form.projectId;
    const saved = isCreate ? await createProject(form) : await updateProject(form.projectId, form);
    ElMessage.success(isCreate ? '项目已创建' : '项目已保存');
    dialogVisible.value = false;
    await loadProjects();
    if (isCreate) switchProject(saved);
    if (!isCreate && String(projectStore.currentProjectId) === String(form.projectId)) projectStore.switchProject(form.projectId);
  } catch (err) {
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(form.projectId ? `项目更新失败，请检查后端项目接口。${detail}` : `项目创建失败，请检查后端项目接口。${detail}`);
  } finally {
    saving.value = false;
  }
}

async function toggleProjectStatus(row: ProjectItem) {
  if (!canManageProject.value) return ElMessage.warning('当前账号没有项目管理权限');
  const enabled = ['ACTIVE', 'ENABLED'].includes(row.status);
  const nextStatus = enabled ? 'DISABLED' : 'ENABLED';
  const actionText = enabled ? '停用' : '启用';
  try {
    await ElMessageBox.confirm(`确认${actionText}项目“${row.name || row.projectName}”？`, `${actionText}项目`, { type: 'warning' });
    await updateProjectStatus(row.projectId, nextStatus);
    ElMessage.success(`项目已${actionText}`);
    await loadProjects();
  } catch (err) {
    if (err === 'cancel' || err === 'close') return;
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`项目${actionText}失败，请检查后端项目状态接口。${detail}`);
  }
}

async function removeProject(row: ProjectItem) {
  if (!canManageProject.value) return ElMessage.warning('当前账号没有项目管理权限');
  try {
    await ElMessageBox.confirm(`确认删除项目“${row.name || row.projectName}”？`, '删除项目', { type: 'warning' });
    deletingId.value = String(row.projectId);
    await deleteProject(row.projectId);
    ElMessage.success('项目已删除');
    await loadProjects();
  } catch (err) {
    if (err === 'cancel' || err === 'close') return;
    const detail = err instanceof Error && err.message ? ` ${err.message}` : '';
    ElMessage.error(`项目删除失败，请检查后端项目接口。${detail}`);
  } finally {
    deletingId.value = '';
  }
}

onMounted(loadProjects);
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div><h2 class="page-title">项目与权限</h2><p class="page-desc">项目档案、成员角色、项目级权限隔离。</p></div>
      <el-button v-if="canManageProject" type="primary" @click="openCreate">新建项目</el-button>
    </div>
    <el-card class="work-card">
      <template #header><strong>项目列表</strong></template>
      <AppTable :loading="loading" :error="error" :data="projects" :columns="[{ prop: 'name', label: '项目名称', slot: 'name' }, { prop: 'code', label: '编码' }, { prop: 'status', label: '状态', slot: 'status' }]">
        <template #empty><EmptyState description="暂无项目，请先创建项目。" /></template>
        <template #name="{ row }">
          <span>{{ row.name || row.projectName }}</span>
          <el-tag v-if="hasSuspiciousText(row.name || row.projectName)" type="warning" size="small" style="margin-left: 6px">疑似历史乱码数据</el-tag>
        </template>
        <template #status="{ row }"><StatusTag :status="row.status" /></template>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="switchProject(row)">设为当前</el-button>
            <el-button v-if="canManageMembers" link type="primary" @click="openMemberManagement(row)">成员管理</el-button>
            <el-button v-if="canManageProject" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canManageProject" link :type="['ACTIVE', 'ENABLED'].includes(row.status) ? 'warning' : 'success'" @click="toggleProjectStatus(row)">
              {{ ['ACTIVE', 'ENABLED'].includes(row.status) ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="canManageProject" link type="danger" :loading="deletingId === String(row.projectId)" @click="removeProject(row)">删除</el-button>
          </template>
        </el-table-column>
      </AppTable>
    </el-card>
    <el-dialog v-model="dialogVisible" title="项目档案" width="560px">
      <el-form label-width="92px">
        <el-form-item label="项目名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="项目编码" required><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="项目地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProject">保存</el-button></template>
    </el-dialog>
    <el-drawer v-model="memberDrawerVisible" size="760px" :title="`${selectedMemberProject?.name || selectedMemberProject?.projectName || '项目'} - 成员管理`">
      <div class="member-toolbar">
        <div class="muted">在当前项目内添加成员、调整项目角色或移除成员。</div>
        <el-button v-if="canManageMembers" type="primary" @click="openAddMember">添加成员</el-button>
      </div>
      <el-alert v-if="memberError" :title="memberError" type="error" show-icon :closable="false" class="member-alert" />
      <AppTable
        :loading="memberLoading"
        :data="members"
        :columns="[
          { prop: 'username', label: '用户名', width: 130 },
          { prop: 'displayName', label: '显示名称', width: 150 },
          { prop: 'projectRole', label: '项目角色', slot: 'role', width: 130 },
          { prop: 'status', label: '状态', slot: 'memberStatus', width: 90 },
          { prop: 'createdAt', label: '加入时间', slot: 'joinedAt' }
        ]"
      >
        <template #empty><EmptyState description="暂无项目成员。" /></template>
        <template #role="{ row }">
          <el-tag size="small" :type="row.projectRole === 'PROJECT_ADMIN' ? 'primary' : row.projectRole === 'VIEWER' ? 'info' : 'success'">
            {{ roleLabel(row.projectRole) }}
          </el-tag>
        </template>
        <template #memberStatus="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">{{ row.status === 'ENABLED' ? '正常' : '停用' }}</el-tag>
        </template>
        <template #joinedAt="{ row }">{{ row.createdAt?.replace('T', ' ').slice(0, 19) || '-' }}</template>
        <el-table-column v-if="canManageMembers" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditMember(row)">修改角色</el-button>
            <el-button link type="danger" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </AppTable>
    </el-drawer>
    <el-dialog v-model="memberDialogVisible" :title="editingUserId == null ? '添加成员' : '修改角色'" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item v-if="editingUserId == null" label="选择用户" required>
          <el-select v-model="memberForm.userId" filterable :loading="userLoading" style="width: 100%" placeholder="请选择用户">
            <el-option v-for="user in availableUsers" :key="user.id" :label="`${user.displayName} (${user.username})`" :value="user.id" />
          </el-select>
          <div v-if="userError" class="form-error">{{ userError }}</div>
        </el-form-item>
        <el-form-item label="项目角色" required>
          <el-select v-model="memberForm.projectRole" style="width: 100%">
            <el-option v-for="role in PROJECT_ROLES" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="memberSubmitting" @click="submitMember">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.member-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.member-alert { margin-bottom: 12px; }
.form-error { width: 100%; margin-top: 6px; color: var(--el-color-danger); font-size: 12px; }
</style>
