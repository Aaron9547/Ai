import { http } from "../plugins/http";
import type { JobTaskAdminRow } from "../types/admin";
import type { MybatisPage } from "../types/page";

export async function fetchJobTasks(params: {
  page?: number;
  size?: number;
  taskType?: string;
  /** 仅列出 payload 中 kbId 匹配的 RAG 流水线任务 */
  ragKbId?: number;
}): Promise<MybatisPage<JobTaskAdminRow>> {
  const { data } = await http.get<MybatisPage<JobTaskAdminRow>>("/api/v1/admin/job-tasks", {
    params: {
      page: params.page ?? 1,
      size: params.size ?? 20,
      ...(params.taskType ? { taskType: params.taskType } : {}),
      ...(params.ragKbId != null ? { ragKbId: params.ragKbId } : {}),
    },
  });
  return data;
}
