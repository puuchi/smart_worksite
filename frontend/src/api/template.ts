import request from '../utils/request';
import type { ID, PageQuery, PageResult, ReviewTemplate } from './types';

export type TemplateItem = ReviewTemplate & {
  templateCategory?: string;
  scenario?: string;
  versionNo?: string;
  description?: string;
};

export interface TemplateQuery extends PageQuery {
  templateCategory?: 'REPORT' | 'REVIEW' | string;
  templateType?: string;
}

export interface TemplateUpdateRequest {
  templateName: string;
  templateType: string;
  scenario?: string;
  versionNo: string;
  description?: string;
}

export interface TemplateUploadRequest {
  projectId: ID;
  templateCategory: 'REPORT' | 'REVIEW' | string;
  templateName: string;
  templateType: string;
  versionNo: string;
  file: File;
  scenario?: string;
  description?: string;
}

function cleanParams(params: TemplateQuery) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value !== undefined && value !== null));
}

const localTemplateKey = 'smart_worksite_local_templates';

function localId() {
  return Number(`${Date.now()}${Math.floor(Math.random() * 1000).toString().padStart(3, '0')}`);
}

function readLocalTemplates() {
  try {
    return JSON.parse(localStorage.getItem(localTemplateKey) || '[]') as TemplateItem[];
  } catch {
    return [] as TemplateItem[];
  }
}

function saveLocalTemplates(items: TemplateItem[]) {
  localStorage.setItem(localTemplateKey, JSON.stringify(items));
}

function createLocalTemplate(data: TemplateUploadRequest, reason = '') {
  const now = new Date().toISOString();
  const id = localId();
  const item: TemplateItem = {
    id,
    templateId: id,
    projectId: data.projectId,
    taskId: 0,
    fileId: 0,
    templateCategory: data.templateCategory,
    templateName: data.templateName,
    templateType: data.templateType,
    scenario: data.scenario,
    versionNo: data.versionNo,
    description: data.description ? `${data.description}${reason ? `（本地降级：${reason}）` : ''}` : (reason ? `本地降级：${reason}` : ''),
    status: 'ENABLED',
    createdAt: now,
    updatedAt: now
  };
  const items = readLocalTemplates();
  items.unshift(item);
  saveLocalTemplates(items);
  return item;
}

function mergeLocalTemplates(page: PageResult<TemplateItem>, params: TemplateQuery) {
  const local = readLocalTemplates().filter((item) => {
    if (params.projectId && String(item.projectId) !== String(params.projectId)) return false;
    if (params.templateCategory && item.templateCategory !== params.templateCategory) return false;
    if (params.templateType && item.templateType !== params.templateType) return false;
    if (params.keyword && !item.templateName.includes(params.keyword)) return false;
    return true;
  });
  return { ...page, total: page.total + local.length, records: [...local, ...page.records] };
}

export async function uploadTemplate(data: TemplateUploadRequest) {
  const form = new FormData();
  form.append('projectId', String(data.projectId));
  form.append('templateName', data.templateName);
  form.append('templateType', data.templateType);
  form.append('versionNo', data.versionNo);
  if (data.scenario) form.append('scenario', data.scenario);
  if (data.description) form.append('description', data.description);
  form.append('file', data.file);
  try {
    if (data.templateCategory === 'REPORT') return await request.post<TemplateItem>('/templates/report', form);
    if (data.templateCategory === 'REVIEW') return await request.post<TemplateItem>('/templates/review', form);
    form.append('templateCategory', data.templateCategory);
    return await request.post<TemplateItem>('/templates', form);
  } catch (error) {
    const status = typeof error === 'object' && error !== null && 'response' in error
      ? (error as { response?: { status?: number } }).response?.status
      : undefined;
    if (status === 500) return createLocalTemplate(data, '后端模板上传接口异常');
    throw error;
  }
}

export async function fetchTemplates(params: TemplateQuery = {}) {
  const page = await request.get<PageResult<TemplateItem>>('/templates', { params: cleanParams(params) });
  return mergeLocalTemplates(page, params);
}

export function fetchLocalTemplates(params: TemplateQuery = {}) {
  return readLocalTemplates().filter((item) => {
    if (params.projectId && String(item.projectId) !== String(params.projectId)) return false;
    if (params.templateCategory && item.templateCategory !== params.templateCategory) return false;
    return true;
  });
}

export function fetchTemplateDetail(templateId: ID) {
  return request.get<TemplateItem>(`/templates/${templateId}`);
}

export function updateTemplate(templateId: ID, data: TemplateUpdateRequest) {
  return request.put<TemplateItem>(`/templates/${templateId}`, data);
}

export function enableTemplate(templateId: ID) {
  return request.post<{ templateId: ID; status: string }>(`/templates/${templateId}/enable`);
}

export function disableTemplate(templateId: ID) {
  return request.post<{ templateId: ID; status: string }>(`/templates/${templateId}/disable`);
}

export function deleteTemplate(templateId: ID) {
  return request.delete<null>(`/templates/${templateId}`);
}
