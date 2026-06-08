import { http } from "../plugins/http";

export type RagLtrTrainRequest = {
  days?: number;
  maxSamples?: number;
};

export type RagLtrTrainResponse = {
  jobTaskId: number;
};

export type RagLtrStatusResponse = {
  ltrModelVersion: string;
  activeLtrFileObjectId: number | null;
  activeLtrJobTaskId: number | null;
  latestJobTaskId: number | null;
  latestJobStatus: string | null;
  latestJobResultJson: string | null;
};

export async function triggerRagLtrTrain(body: RagLtrTrainRequest = {}): Promise<RagLtrTrainResponse> {
  const { data } = await http.post<RagLtrTrainResponse>("/api/v1/admin/rag-ltr/train", body);
  return data;
}

export async function fetchRagLtrStatus(): Promise<RagLtrStatusResponse> {
  const { data } = await http.get<RagLtrStatusResponse>("/api/v1/admin/rag-ltr/status");
  return data;
}
