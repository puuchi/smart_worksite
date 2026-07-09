import request from '../utils/request';
import { mockFiles } from '../mocks/file';
import type { FileObject, ID, PageQuery, PageResult } from './types';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_FILE_MOCK', false);

export interface FileAccessUrl {
  fileId: ID;
  url: string;
  expiresAt?: string;
  previewSupported?: boolean;
}

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

interface FileObjectResponse {
  fileId: number;
  projectId: number;
  bizType?: string;
  bizId?: number;
  fileName: string;
  fileExt?: string;
  contentType?: string;
  fileSize?: number;
  fileHash?: string;
  status: string;
  metadata?: string;
  previewSupported?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

function mapFile(item: FileObjectResponse): FileObject {
  return {
    id: item.fileId,
    fileId: item.fileId,
    projectId: item.projectId,
    originalName: item.fileName,
    fileType: item.fileExt || item.contentType || '',
    size: item.fileSize || 0,
    status: item.status as FileObject['status'],
    createdAt: item.createdAt || '',
    updatedAt: item.updatedAt || item.createdAt || ''
  };
}

function mapFilePage(page: PageResult<FileObjectResponse>): PageResult<FileObject> {
  return { ...page, records: page.records.map(mapFile) };
}

function triggerExternalDownload(url: string, filename?: string) {
  const link = document.createElement('a');
  link.href = url;
  if (filename) link.download = filename;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export async function uploadFile(projectId: ID, file: File, businessType = 'KNOWLEDGE_DOC') {
  if (useMock) return { ...mockFiles[0], projectId, originalName: file.name } satisfies FileObject;
  const form = new FormData();
  form.append('projectId', String(projectId));
  form.append('bizType', businessType);
  form.append('file', file);
  const uploaded = await request.post<FileObjectResponse>('/files', form);
  return mapFile(uploaded);
}

export async function fetchFiles(params: PageQuery & { bizType?: string; bizId?: ID } = {}) {
  if (useMock) return { pageNo: params.pageNo || 1, pageSize: params.pageSize || 20, total: mockFiles.length, records: mockFiles } satisfies PageResult<FileObject>;
  if (!params.projectId) throw new Error('文件列表缺少 projectId');
  const requestParams = Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value !== undefined && value !== null));
  const page = await request.get<PageResult<FileObjectResponse>>('/files', { params: requestParams });
  return mapFilePage(page);
}

export async function fetchFileDetail(fileId: ID) {
  if (useMock) return mockFiles.find((item) => String(item.fileId) === String(fileId)) || mockFiles[0];
  const file = await request.get<FileObjectResponse>(`/files/${fileId}`);
  return mapFile(file);
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
  if (useMock) {
    const blob = new Blob(['mock file content'], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    triggerExternalDownload(url, filename || 'mock.txt');
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    return;
  }
  const { url } = await fetchFileDownloadUrl(fileId);
  if (!url) throw new Error('文件下载地址为空，请检查后端文件访问 URL 接口。');
  triggerExternalDownload(url, filename);
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
