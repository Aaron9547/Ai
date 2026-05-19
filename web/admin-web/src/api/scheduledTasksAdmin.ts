import { http } from "@/plugins/http";

export interface ScheduledTaskEnumOption {
  code: string;
  label: string;
  intervalDays?: number;
}

export interface ScheduledTaskMeta {
  executors: ScheduledTaskEnumOption[];
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
}

export async function fetchScheduledTaskMeta(): Promise<ScheduledTaskMeta> {
  const { data } = await http.get<ScheduledTaskMeta>("/api/v1/admin/scheduled-tasks/meta");
  return data;
}

export async function fetchScheduledTasks(executorCode?: string): Promise<ScheduledTaskRow[]> {
  const { data } = await http.get<ScheduledTaskRow[]>("/api/v1/admin/scheduled-tasks", {
    params: executorCode ? { executorCode } : undefined,
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

export async function runScheduledTaskNow(id: number): Promise<void> {
  await http.post(`/api/v1/admin/scheduled-tasks/${id}/run`);
}
