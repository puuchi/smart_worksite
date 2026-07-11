import request, { downloadFile } from '../utils/request';
import { mockFiles } from '../mocks/file';
import type { FileAccessUrl, FileObject, ID, PageQuery, PageResult } from './types';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_FILE_MOCK', false);

export interface FileParseRecord {
  recordId: ID;
  projectId: ID;
  fileId: ID;
  sourceFileHash?: string;
  sourceContentType?: string;
  parseType?: string;
  resultFormat?: string;
  parserProvider?: string;
  parserModel?: string;
  status: string;
  progress?: number;
  currentStage?: string;
  resultFileId?: ID;
  contentPreview?: string;
  errorMessage?: string;
  metadata?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FileParseContent {
  recordId: ID;
  resultFormat: string;
  content: string;
}

function cleanParams(params: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value !== undefined && value !== null));
}

export async function uploadFile(projectId: ID, file: File, businessType = 'KNOWLEDGE_DOC') {
  if (useMock) return { ...mockFiles[0], projectId, fileName: file.name } satisfies FileObject;
  const form = new FormData();
  form.append('projectId', String(projectId));
  form.append('bizType', businessType);
  form.append('file', file);
  return request.post<FileObject>('/files', form);
}

export async function fetchFiles(params: PageQuery & { bizType?: string; bizId?: ID } = {}) {
  if (useMock) return { pageNo: params.pageNo || 1, pageSize: params.pageSize || 20, total: mockFiles.length, records: mockFiles } satisfies PageResult<FileObject>;
  if (!params.projectId) throw new Error('?????? projectId');
  return request.get<PageResult<FileObject>>('/files', { params: cleanParams(params as Record<string, unknown>) });
}

export async function fetchFileDetail(fileId: ID) {
  if (useMock) return mockFiles.find((item) => String(item.fileId) === String(fileId)) || mockFiles[0];
  return request.get<FileObject>(`/files/${fileId}`);
}

export async function fetchFileDownloadUrl(fileId: ID) {
  return request.get<FileAccessUrl>(`/files/${fileId}/access-url`, { params: { usage: 'DOWNLOAD' } });
}

export async function fetchFilePreviewUrl(fileId: ID) {
  return request.get<FileAccessUrl>(`/files/${fileId}/access-url`, { params: { usage: 'PREVIEW' } });
}

export async function deleteFile(fileId: ID) {
  if (useMock) return null;
  return request.delete<null>(`/files/${fileId}`);
}

export async function downloadByFileId(fileId: ID, filename?: string) {
  if (useMock) return downloadFile('', { filename, data: 'mock file content' });
  const access = await fetchFileDownloadUrl(fileId);
  if (!access.url) throw new Error('?????????????????? URL ???');
  return downloadFile(access.url, { filename });
}

export async function createFileParse(fileId: ID, data: { projectId: ID; force?: boolean; targetFormat?: string; language?: string }) {
  return request.post<FileParseRecord>(`/files/${fileId}/parse`, data);
}

export async function fetchFileParseRecords(fileId: ID, projectId: ID) {
  return request.get<FileParseRecord[]>(`/files/${fileId}/parse-records`, { params: { projectId } });
}

export async function fetchLatestFileParseRecord(fileId: ID, projectId: ID) {
  return request.get<FileParseRecord>(`/files/${fileId}/parse-records/latest`, { params: { projectId } });
}

export async function fetchFileParseRecord(recordId: ID) {
  return request.get<FileParseRecord>(`/file-parse-records/${recordId}`);
}

export async function fetchFileParseContent(recordId: ID) {
  return request.get<FileParseContent>(`/file-parse-records/${recordId}/content`);
}

export async function retryFileParse(recordId: ID) {
  return request.post<FileParseRecord>(`/file-parse-records/${recordId}/retry`);
}
