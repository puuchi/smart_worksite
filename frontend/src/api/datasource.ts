import request from '../utils/request';
import type { DataSourceItem, DataSourceQueryResult, ID, PageQuery, PageResult } from './types';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_DATASOURCE_MOCK', false);

const mockSources: DataSourceItem[] = [
  { id: 1, dataSourceId: 1, projectId: 1001, name: '???????', type: 'MYSQL', host: '127.0.0.1', databaseName: 'worksite_quality', status: 'ACTIVE', description: '??????????', createdAt: '2026-07-01T09:00:00+08:00', updatedAt: '2026-07-01T09:00:00+08:00' }
];

export async function fetchDataSources(params: PageQuery = {}) {
  if (useMock) {
    const records = mockSources.filter((item) => !params.projectId || String(item.projectId) === String(params.projectId));
    return { pageNo: params.pageNo || 1, pageSize: params.pageSize || 20, total: records.length, records } satisfies PageResult<DataSourceItem>;
  }
  return request.get<PageResult<DataSourceItem>>('/data-sources', { params });
}

export async function createDataSource(data: Partial<DataSourceItem> & { password?: string }) {
  if (useMock) return { ...mockSources[0], ...data, id: Date.now(), dataSourceId: Date.now(), status: 'ACTIVE', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() } as DataSourceItem;
  return request.post<DataSourceItem>('/data-sources', data);
}

export async function testDataSource(dataSourceId: ID) {
  if (useMock) return { dataSourceId, success: true, message: '????' };
  return request.post<{ dataSourceId: ID; success: boolean; message: string }>(`/data-sources/${dataSourceId}/test`);
}

export async function queryDataSource(data: { projectId: ID; question: string; dataSourceIds?: ID[] }) {
  if (useMock) return { sql: "select count(*) as issue_count from safety_issue where status <> 'CLOSED';", columns: ['issue_count'], rows: [{ issue_count: 12 }], summary: '????????? 12 ??' } satisfies DataSourceQueryResult;
  return request.post<DataSourceQueryResult>('/ai/database/query', data);
}
