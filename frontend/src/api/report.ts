import request, { downloadFile } from '../utils/request';
import { mockReports } from '../mocks/report';
import type { ID, PageQuery, PageResult, ReportItem } from './types';
import { fetchLocalTemplates, type TemplateItem } from './template';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_REPORT_MOCK', false);

interface ReportCreateRequest {
  projectId: ID;
  reportName: string;
  reportType: string;
  templateId: ID;
  knowledgeBaseIds?: ID[];
  dataSourceIds?: ID[];
  referenceFileIds?: ID[];
  variables?: Record<string, unknown>;
}

export type ReportTemplate = TemplateItem;
type DownloadUrlResponse = string | { url: string; [key: string]: unknown };

export async function uploadReportTemplate(data: { projectId: ID; templateName: string; templateType: string; file: File; scenario?: string; versionNo?: string; description?: string }) {
  if (useMock) return { templateId: Date.now(), projectId: data.projectId, fileId: 4300, status: 'SUCCESS' } satisfies Partial<ReportTemplate>;
  const form = new FormData();
  form.append('projectId', String(data.projectId));
  form.append('templateName', data.templateName);
  form.append('templateType', data.templateType);
  if (data.scenario) form.append('scenario', data.scenario);
  if (data.versionNo) form.append('versionNo', data.versionNo);
  if (data.description) form.append('description', data.description);
  form.append('file', data.file);
  return request.post<ReportTemplate>('/report/templates', form);
}

export async function fetchReportTemplates(projectId?: ID) {
  if (useMock) return [] as ReportTemplate[];
  const remote = await request.get<ReportTemplate[]>('/report/templates', { params: { projectId } });
  return [...fetchLocalTemplates({ projectId, templateCategory: 'REPORT' }), ...remote] as ReportTemplate[];
}

export async function fetchReportTemplateVariables(templateId: ID) {
  if (useMock) return ['projectName', 'month'];
  return request.get<string[]>(`/report/templates/${templateId}/variables`);
}

export async function createReport(data: ReportCreateRequest) {
  if (useMock) return { reportId: 10003, taskId: 9503, status: 'PROCESSING' };
  return request.post<{ reportId: ID; taskId: ID; status: string }>('/reports', data);
}

export async function fetchReports(params: PageQuery = {}) {
  const records = mockReports.filter((item) => !params.projectId || String(item.projectId) === String(params.projectId));
  if (useMock) return { pageNo: params.pageNo || 1, pageSize: params.pageSize || 20, total: records.length, records } satisfies PageResult<ReportItem>;
  return request.get<PageResult<ReportItem>>('/reports', { params });
}

export async function fetchReportDetail(reportId: ID) {
  if (useMock) return mockReports.find((item) => String(item.reportId) === String(reportId)) || mockReports[0];
  return request.get<ReportItem>(`/reports/${reportId}`);
}

export async function regenerateReport(reportId: ID) {
  if (useMock) return { reportId, taskId: 9504, status: 'PROCESSING' };
  return request.post<{ reportId: ID; taskId: ID; status: string }>(`/reports/${reportId}/regenerate`);
}

export async function fetchReportDownloadUrl(reportId: ID, format: 'WORD' | 'PDF' = 'WORD') {
  const result = await request.get<DownloadUrlResponse>(`/reports/${reportId}/download`, { params: { format } });
  return typeof result === 'string' ? result : result.url;
}

export async function downloadReport(reportId: ID, format: 'WORD' | 'PDF' = 'WORD', filename?: string) {
  if (useMock) return downloadFile('', { filename: filename || `report-${reportId}.${format === 'PDF' ? 'pdf' : 'docx'}`, data: 'mock report content' });
  const url = await fetchReportDownloadUrl(reportId, format);
  if (!url) throw new Error('报告下载地址为空，请检查后端报告下载接口');
  return downloadFile(url, { filename });
}
