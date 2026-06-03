import { http } from "@/plugins/http";

export type ScheduledTaskCategoryCode = "TENANT_CRON" | "CHAT_USER_REMINDER";

export interface ScheduledTaskEnumOption {
  code: string;
  label: string;
  taskCategory?: ScheduledTaskCategoryCode;
  intervalDays?: number;
}

export interface ScheduledTaskCategoryOption {
  code: ScheduledTaskCategoryCode;
  label: string;
}

export interface ScheduledTaskMeta {
  categories: ScheduledTaskCategoryOption[];
  executors: ScheduledTaskEnumOption[];
}

export interface ScheduledRunSummary {
  runId: number;
  status: string;
  progressJson?: string | null;
}

export interface TaskProgress {
  stage?: string;
  message?: string;
  percent?: number | null;
  current?: number | null;
  total?: number | null;
}

export interface ScheduledRunDetail {
  id: number;
  registrationId: number;
  executorCode: string;
  executorLabel: string;
  status: string;
  triggerType: string;
  progressJson?: string | null;
  childJobTaskIdsJson?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
}

export interface ScheduledTaskRow {
  id: number;
  executorCode: string;
  executorLabel: string;
  name: string;
  cronExpression: string;
  enabled: boolean;
  lastExecAt?: string | null;
  nextExecAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  activeRun?: ScheduledRunSummary | null;
}

export interface ScheduledRunTriggerResult {
  accepted: boolean;
  duplicate: boolean;
  runId: number;
  status: string;
  run: ScheduledRunDetail;
}

export function parseTaskProgress(json?: string | null): TaskProgress | null {
  if (!json?.trim()) return null;
  try {
    return JSON.parse(json) as TaskProgress;
  } catch {
    return null;
  }
}

export async function fetchScheduledTaskMeta(): Promise<ScheduledTaskMeta> {
  const { data } = await http.get<ScheduledTaskMeta>("/api/v1/admin/scheduled-tasks/meta");
  return data;
}

export async function fetchScheduledTasks(opts?: {
  executorCode?: string;
  taskCategory?: ScheduledTaskCategoryCode;
}): Promise<ScheduledTaskRow[]> {
  const params: Record<string, string> = {};
  if (opts?.executorCode) params.executorCode = opts.executorCode;
  if (opts?.taskCategory) params.taskCategory = opts.taskCategory;
  const { data } = await http.get<ScheduledTaskRow[]>("/api/v1/admin/scheduled-tasks", {
    params: Object.keys(params).length ? params : undefined,
  });
  return data;
}

export async function createScheduledTask(body: {
  executorCode: string;
  name: string;
  cronExpression: string;
  enabled?: boolean;
}): Promise<ScheduledTaskRow> {
  const { data } = await http.post<ScheduledTaskRow>("/api/v1/admin/scheduled-tasks", body);
  return data;
}

export async function updateScheduledTask(
  id: number,
  body: Partial<{
    name: string;
    cronExpression: string;
    enabled: boolean;
  }>,
): Promise<ScheduledTaskRow> {
  const { data } = await http.put<ScheduledTaskRow>(`/api/v1/admin/scheduled-tasks/${id}`, body);
  return data;
}

export async function deleteScheduledTask(id: number): Promise<void> {
  await http.delete(`/api/v1/admin/scheduled-tasks/${id}`);
}

export async function runScheduledTaskNow(id: number): Promise<ScheduledRunTriggerResult> {
  const { data } = await http.post<ScheduledRunTriggerResult>(`/api/v1/admin/scheduled-tasks/${id}/run`);
  return data;
}

export async function fetchActiveScheduledRun(registrationId: number): Promise<ScheduledRunDetail | null> {
  const { data } = await http.get<ScheduledRunDetail | "">(
    `/api/v1/admin/scheduled-tasks/${registrationId}/run/active`,
  );
  if (!data || typeof data !== "object") return null;
  return data;
}

export async function fetchScheduledRun(runId: number): Promise<ScheduledRunDetail> {
  const { data } = await http.get<ScheduledRunDetail>(`/api/v1/admin/scheduled-tasks/runs/${runId}`);
  return data;
}
