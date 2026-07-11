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

function mergeLocalTemplates(page: PageResult<TemplateItem>, params: TemplateQuery) {
  const local: TemplateItem[] = [];
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
  if (data.templateCategory === 'REPORT') return request.post<TemplateItem>('/templates/report', form);
  if (data.templateCategory === 'REVIEW') return request.post<TemplateItem>('/templates/review', form);
  form.append('templateCategory', data.templateCategory);
  return request.post<TemplateItem>('/templates', form);
}

export async function fetchTemplates(params: TemplateQuery = {}) {
  const page = await request.get<PageResult<TemplateItem>>('/templates', { params: cleanParams(params) });
  return mergeLocalTemplates(page, params);
}

export function fetchLocalTemplates(_params: TemplateQuery = {}) {
  return [] as TemplateItem[];
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
