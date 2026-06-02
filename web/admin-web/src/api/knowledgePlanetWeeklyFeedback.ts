import { http } from "@/plugins/http";

export type BookRecommendation = {
  title: string;
  reason?: string;
  url?: string;
  source?: string;
  matchedReferenceTitle?: string;
};

export type KnowledgeWeeklyPlan = {
  summary: string;
  inferredPersona?: string;
  evidenceTopics?: string[];
  progressNotes?: string;
  thinkDirections: string[];
  gapAreas: string[];
  bookRecommendations: BookRecommendation[];
  bookSearchQuery?: string;
};

export type WeeklyInsightDetail = {
  weekStart: string;
  status: string;
  plan: KnowledgeWeeklyPlan;
  computedAt: string | null;
};

export type WeeklyFeedbackEntry = {
  userId: number;
  loginName: string;
  displayName: string;
  weekStart: string;
  helpful: boolean;
  at: string | null;
};

export type WeeklyFeedbackPageResult = {
  records: WeeklyFeedbackEntry[];
  total: number;
  page: number;
  size: number;
  helpfulCount: number;
  notHelpfulCount: number;
};

export async function listKnowledgePlanetWeeklyFeedback(params: {
  page?: number;
  size?: number;
  keyword?: string;
}): Promise<WeeklyFeedbackPageResult> {
  const { data } = await http.get<WeeklyFeedbackPageResult>(
    "/api/v1/admin/user-profiles/knowledge-planet-weekly-feedback",
    { params },
  );
  return data;
}

export async function fetchKnowledgePlanetWeeklyInsight(
  userId: number,
  weekStart: string,
): Promise<WeeklyInsightDetail> {
  const { data } = await http.get<WeeklyInsightDetail>(
    `/api/v1/admin/user-profiles/${userId}/knowledge-planet-weekly`,
    { params: { weekStart } },
  );
  return data;
}
