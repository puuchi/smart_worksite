import request from '../utils/request';
import { mockQaMessages, mockQaSessions, type QaMessageWithExtra } from '../mocks/qa';
import type { ID, PageResult, QaMessage, QaSession } from './types';
import { useModuleMock } from './mock';

const useMock = useModuleMock('VITE_USE_QA_MOCK', false);
const mockSessions = [...mockQaSessions];
const mockMessages: QaMessageWithExtra[] = [...mockQaMessages];
const feedbackState: Record<string, boolean> = {};

function mockId() {
  return Number(`${Date.now()}${Math.floor(Math.random() * 1000).toString().padStart(3, '0')}`);
}

function buildAssistantMessage(sessionId: ID, projectId: ID, question: string): QaMessageWithExtra {
  const now = new Date().toISOString();
  const id = mockId();
  return {
    messageId: id,
    sessionId,
    projectId,
    question,
    answer: `???????????${question}??????????????????????????`,
    routeMode: 'MIXED',
    status: 'SUCCESS',
    createdAt: now,
    updatedAt: now,
    references: [
      { title: '???????', sourceType: 'KNOWLEDGE', page: '?5?', score: 0.91, documentId: 3001 },
      { title: '???????', sourceType: 'KNOWLEDGE', page: '?2?', score: 0.84, documentId: 3002 }
    ],
    sqlResult: { sql: 'select risk_level,count(*) as total from check_items group by risk_level', table: 'check_items', rows: [{ risk_level: 'HIGH', total: 2 }, { risk_level: 'MEDIUM', total: 5 }] }
  };
}

export async function createQaSession(data: { projectId: ID; title?: string }) {
  if (useMock) {
    const now = new Date().toISOString();
    const id = mockId();
    const created = { sessionId: id, projectId: data.projectId, title: data.title || '????', status: 'ACTIVE', createdAt: now, updatedAt: now } satisfies QaSession;
    mockSessions.unshift(created);
    return created;
  }
  return request.post<QaSession>('/qa/sessions', data);
}

export async function fetchQaSessions(projectId: ID) {
  if (useMock) return mockSessions.filter((item) => String(item.projectId) === String(projectId));
  const page = await request.get<PageResult<QaSession>>('/qa/sessions', { params: { projectId } });
  return page.records;
}

export async function fetchQaMessages(sessionId: ID) {
  if (useMock) return mockMessages.filter((item) => String(item.sessionId) === String(sessionId));
  return request.get<QaMessage[]>(`/qa/sessions/${sessionId}/messages`);
}

export async function sendQuestion(sessionId: ID, data: { projectId: ID; question: string; routeMode?: string; dataSourceIds?: ID[]; knowledgeBaseIds?: ID[] }) {
  if (useMock) {
    const answer = buildAssistantMessage(sessionId, data.projectId, data.question);
    mockMessages.push(answer);
    return answer;
  }
  return request.post<QaMessage>(`/qa/sessions/${sessionId}/messages`, data);
}

export async function submitFeedback(messageId: ID, useful: boolean) {
  if (useMock) {
    feedbackState[String(messageId)] = useful;
    return { messageId, useful };
  }
  return request.post(`/qa/messages/${messageId}/feedback`, {
    feedbackType: useful ? 'LIKE' : 'DISLIKE',
    extra: { useful }
  });
}
