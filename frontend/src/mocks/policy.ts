import type { PolicyArticle, PolicyCrawlTask, PolicySource } from '../api/types';

export const mockPolicySources: PolicySource[] = [
  {
    sourceId: 101,
    projectId: 1001,
    name: '住建部政策公开栏目',
    url: 'https://www.mohurd.gov.cn/gongkai/zhengce/',
    crawlFrequency: 'DAILY',
    status: 'ENABLED',
    lastCrawledAt: '2026-07-10T09:30:00+08:00',
    createdAt: '2026-07-01T09:00:00+08:00',
    updatedAt: '2026-07-10T09:30:00+08:00'
  },
  {
    sourceId: 102,
    projectId: 1001,
    name: '省住建厅安全生产通知',
    url: 'https://zjt.example.gov.cn/safety',
    crawlFrequency: 'WEEKLY',
    status: 'ENABLED',
    lastCrawledAt: '2026-07-08T15:10:00+08:00',
    createdAt: '2026-07-02T10:20:00+08:00',
    updatedAt: '2026-07-08T15:10:00+08:00'
  }
];

export const mockPolicyTasks: PolicyCrawlTask[] = [
  {
    taskId: 900101,
    projectId: 1001,
    sourceId: 101,
    sourceName: '住建部政策公开栏目',
    status: 'SUCCESS',
    progress: 100,
    fetchedCount: 8,
    indexedCount: 8,
    message: '已完成政策资讯采集并进入知识更新队列。',
    startedAt: '2026-07-10T09:30:00+08:00',
    finishedAt: '2026-07-10T09:32:00+08:00',
    createdAt: '2026-07-10T09:30:00+08:00'
  }
];

export const mockPolicyArticles: PolicyArticle[] = [
  {
    articleId: 700101,
    projectId: 1001,
    sourceId: 101,
    title: '建筑施工安全生产治本攻坚行动提示',
    url: 'https://www.mohurd.gov.cn/gongkai/zhengce/safety-2026-01.html',
    summary: '围绕危大工程、临边洞口、起重机械和高处作业提出专项治理要求。',
    publishDate: '2026-07-09',
    category: '安全生产',
    indexStatus: 'SUCCESS',
    createdAt: '2026-07-10T09:32:00+08:00',
    updatedAt: '2026-07-10T09:32:00+08:00'
  },
  {
    articleId: 700102,
    projectId: 1001,
    sourceId: 102,
    title: '施工现场扬尘治理与文明施工检查要点',
    url: 'https://zjt.example.gov.cn/safety/dust-2026.html',
    summary: '明确围挡、喷淋、裸土覆盖、车辆冲洗和在线监测的检查要求。',
    publishDate: '2026-07-08',
    category: '文明施工',
    indexStatus: 'INDEXING',
    createdAt: '2026-07-08T15:12:00+08:00',
    updatedAt: '2026-07-08T15:12:00+08:00'
  }
];
