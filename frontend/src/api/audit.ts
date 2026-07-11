import request from '../utils/request';
import type { AuditLog, PageQuery, PageResult } from './types';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_AUDIT_MOCK', true);

const mockLogs: AuditLog[] = [
  { id: 1, projectId: 1001, operatorName: '项目管理员', action: 'REPORT_CREATE', module: 'report', targetType: 'REPORT', targetId: 10001, result: 'SUCCESS', ip: '127.0.0.1', createdAt: '2026-07-06T10:12:00+08:00' },
  { id: 2, projectId: 1001, operatorName: '安全员', action: 'REVIEW_SUBMIT', module: 'review', targetType: 'REVIEW_RECORD', targetId: 7001, result: 'SUCCESS', ip: '127.0.0.1', createdAt: '2026-07-06T11:20:00+08:00' }
];

export async function fetchAuditLogs(params: PageQuery & { module?: string; action?: string; operatorName?: string; startTime?: string; endTime?: string } = {}) {
  if (useMock) {
    const records = mockLogs.filter((item) => !params.projectId || String(item.projectId) === String(params.projectId));
    return { pageNo: params.pageNo || 1, pageSize: params.pageSize || 20, total: records.length, records } satisfies PageResult<AuditLog>;
  }
  return request.get<PageResult<AuditLog>>('/audit/logs', { params });
}
