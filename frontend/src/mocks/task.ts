import type { TaskDetail, TaskStageLog } from '../api/types';

export const mockTaskStages: TaskStageLog[] = [
  { id: 1, taskId: 9502, attemptNo: 1, stageCode: 'COLLECT_CONTEXT', status: 'SUCCESS', outputSummary: '项目数据和知识库引用收集完成', createdAt: '2026-07-04T09:00:00+08:00' },
  { id: 2, taskId: 9502, attemptNo: 1, stageCode: 'TEMPLATE_RENDERING', status: 'RUNNING', outputSummary: '正在渲染报告模板', createdAt: '2026-07-04T09:05:00+08:00' }
];

export const mockTaskDetail: TaskDetail = {
  projectId: 1001,
  taskId: 9502,
  taskType: 'REPORT_GENERATE',
  bizType: 'REPORT',
  bizId: 10002,
  status: 'RUNNING',
  currentStage: 'TEMPLATE_RENDERING',
  createdAt: '2026-07-04T09:00:00+08:00',
  updatedAt: '2026-07-04T09:15:00+08:00'
};
