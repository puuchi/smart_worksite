import type { QaMessage, QaSession } from '../api/types';

export type QaMessageWithExtra = QaMessage & Record<string, unknown>;

export const mockQaSessions: QaSession[] = [
  { sessionId: 5001, projectId: 1001, title: '安全规范问答', status: 'ACTIVE', createdAt: '2026-07-04T09:00:00+08:00', updatedAt: '2026-07-04T09:30:00+08:00' },
  { sessionId: 5002, projectId: 1001, title: '质量验收咨询', status: 'ACTIVE', createdAt: '2026-07-04T10:00:00+08:00', updatedAt: '2026-07-04T10:30:00+08:00' }
];

export const mockQaMessages: QaMessageWithExtra[] = [
  { messageId: 6001, sessionId: 5001, projectId: 1001, role: 'user', content: '临边洞口防护有哪些验收要点？', question: '临边洞口防护有哪些验收要点？', status: 'SUCCESS', createdAt: '2026-07-04T09:01:00+08:00', updatedAt: '2026-07-04T09:01:00+08:00' },
  { messageId: 6002, sessionId: 5001, projectId: 1001, role: 'assistant', content: '临边防护应设置防护栏杆、挡脚板和安全网；洞口应使用盖板或围栏封闭，并设置醒目标识。', answer: '临边防护应设置防护栏杆、挡脚板和安全网；洞口应使用盖板或围栏封闭，并设置醒目标识。', routeMode: 'MIXED', references: [{ title: 'JGJ 80-2016 高处作业安全技术规范', sourceType: 'KNOWLEDGE', page: '第12页', score: 0.92, documentId: 3001 }], status: 'SUCCESS', createdAt: '2026-07-04T09:02:00+08:00', updatedAt: '2026-07-04T09:02:00+08:00' },
  { messageId: 6003, sessionId: 5002, projectId: 1001, role: 'assistant', content: '质量验收可围绕检验批、分项工程和隐蔽验收记录提问。', answer: '质量验收可围绕检验批、分项工程和隐蔽验收记录提问。', routeMode: 'KNOWLEDGE', references: [{ title: '混凝土结构工程施工质量验收规范', sourceType: 'KNOWLEDGE', page: '第8页', score: 0.88, documentId: 3010 }], status: 'SUCCESS', createdAt: '2026-07-04T10:02:00+08:00', updatedAt: '2026-07-04T10:02:00+08:00' }
];
