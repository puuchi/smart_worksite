import request from '../utils/request';
import type { ID, PageQuery, PageResult, ProjectItem, ProjectMember } from './types';
import { mockProjects } from '../mocks/project';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_PROJECT_MOCK', false);

interface ProjectResponse {
  projectId: number;
  projectName: string;
  projectCode: string;
  location?: string;
  status: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

function mapProject(item: ProjectResponse): ProjectItem {
  return {
    id: item.projectId,
    projectId: item.projectId,
    name: item.projectName,
    code: item.projectCode,
    status: item.status as ProjectItem['status'],
    address: item.location || '',
    description: item.description || '',
    createdAt: item.createdAt || '',
    updatedAt: item.updatedAt || item.createdAt || ''
  };
}

function mapProjectPage(page: PageResult<ProjectResponse>): PageResult<ProjectItem> {
  return { ...page, records: page.records.map(mapProject) };
}

export interface ProjectFormPayload {
  name: string;
  code: string;
  address?: string;
  description?: string;
}

function toBackendProject(data: ProjectFormPayload) {
  return {
    projectName: data.name.trim(),
    projectCode: data.code.trim(),
    location: data.address?.trim() || undefined,
    description: data.description?.trim() || undefined
  };
}

export async function fetchProjects(params: PageQuery = {}) {
  if (useMock) return { pageNo: params.pageNo || 1, pageSize: params.pageSize || 20, total: mockProjects.length, records: mockProjects } satisfies PageResult<ProjectItem>;
  const page = await request.get<PageResult<ProjectResponse>>('/projects', { params });
  return mapProjectPage(page);
}

export async function fetchProjectDetail(projectId: ID) {
  if (useMock) return mockProjects.find((item) => String(item.projectId) === String(projectId)) || mockProjects[0];
  const project = await request.get<ProjectResponse>(`/projects/${projectId}`);
  return mapProject(project);
}

export async function createProject(data: ProjectFormPayload) {
  if (useMock) return { ...mockProjects[0], ...data, id: Date.now(), projectId: Date.now(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() } as ProjectItem;
  const project = await request.post<ProjectResponse>('/projects', toBackendProject(data));
  return mapProject(project);
}

export async function updateProject(projectId: ID, data: ProjectFormPayload) {
  if (useMock) return { ...(mockProjects.find((item) => String(item.projectId) === String(projectId)) || mockProjects[0]), ...data } as ProjectItem;
  const project = await request.put<ProjectResponse>(`/projects/${projectId}`, toBackendProject(data));
  return mapProject(project);
}

const mockMembers: ProjectMember[] = [
  { id: 1, userId: 1, projectId: 1001, realName: '项目管理员', roleName: '项目管理员', permissions: ['*'], status: 'ACTIVE', createdAt: '2026-07-01T09:00:00+08:00' },
  { id: 2, userId: 2, projectId: 1001, realName: '安全员', roleName: '安全员', permissions: ['review:view', 'ocr:view'], status: 'ACTIVE', createdAt: '2026-07-02T09:00:00+08:00' }
];

export async function fetchProjectMembers(projectId: ID) {
  if (useMock) return mockMembers.filter((item) => String(item.projectId) === String(projectId));
  void projectId;
  return [] as ProjectMember[];
}

export async function addProjectMember(projectId: ID, data: { userId: ID; roleName: string }) {
  if (useMock) return { id: Date.now(), projectId, userId: data.userId, realName: `用户${data.userId}`, roleName: data.roleName, permissions: [], status: 'ACTIVE', createdAt: new Date().toISOString() } satisfies ProjectMember;
  throw new Error('项目成员接口待后端提供');
}

export async function fetchProjectPermissions(projectId: ID) {
  if (useMock) return ['dashboard:view', 'project:view', 'file:view', 'template:view', 'knowledge:view', 'qa:view', 'review:view', 'report:view', 'ocr:view', 'datasource:view', 'task:view', 'audit:view'];
  void projectId;
  return [] as string[];
}
