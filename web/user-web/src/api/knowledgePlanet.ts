import { http } from "../plugins/http";

export type KnowledgePlanetNodeSummary = {
  id: number;
  title: string;
  summary: string;
  createdAt: string;
};

/** 全屏转场锚点（冻结 getBoundingClientRect，避免 DOMRect 在 props 中失真） */
export type KnowledgePlanetWarpOrigin = {
  left: number;
  top: number;
  width: number;
  height: number;
};

export function warpOriginFromRect(rect: DOMRect): KnowledgePlanetWarpOrigin {
  return { left: rect.left, top: rect.top, width: rect.width, height: rect.height };
}

export function warpOriginCenter(o: KnowledgePlanetWarpOrigin): { x: number; y: number } {
  return { x: o.left + o.width / 2, y: o.top + o.height / 2 };
}

export type KnowledgePlanetSummary = {
  enabled: boolean;
  nodeCount: number;
  planetCount: number;
  dominantPlanetName: string;
  recentNodes: KnowledgePlanetNodeSummary[];
  weeklySummary: string;
  emailEligible: boolean;
};

export type KnowledgePlanetView = {
  id: string;
  name: string;
  nodeCount: number;
  colorRgb: number;
  displaySize: number;
  summary: string;
};

export type KnowledgeGraphNode = {
  id: string;
  kind: "planet" | "knowledge";
  planetId: string | null;
  knowledgeNodeId: number | null;
  title: string;
  summary: string;
  topicTags: string[];
};

export type KnowledgeGraphLink = {
  sourceId: string;
  targetId: string;
  kind: "orbit" | "relation";
};

export type KnowledgePlanetUniverse = {
  planets: KnowledgePlanetView[];
  nodes: KnowledgeGraphNode[];
  links: KnowledgeGraphLink[];
};

export type BookRecommendation = {
  title: string;
  reason: string;
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

export type KnowledgePlanetWeeklyLatest = {
  weekStart: string | null;
  status: string | null;
  plan: KnowledgeWeeklyPlan | null;
};

export async function fetchKnowledgePlanetSummary(): Promise<KnowledgePlanetSummary> {
  const { data } = await http.get<KnowledgePlanetSummary>("/open/v1/chat/knowledge-planet/summary");
  return data;
}

export async function fetchKnowledgePlanetUniverse(): Promise<KnowledgePlanetUniverse> {
  const { data } = await http.get<KnowledgePlanetUniverse>("/open/v1/chat/knowledge-planet/universe");
  return data;
}

export async function fetchKnowledgePlanetWeeklyLatest(): Promise<KnowledgePlanetWeeklyLatest> {
  const { data } = await http.get<KnowledgePlanetWeeklyLatest>(
    "/open/v1/chat/knowledge-planet/weekly/latest",
  );
  return data;
}

export async function postKnowledgePlanetWeeklyFeedback(helpful: boolean): Promise<void> {
  await http.post("/open/v1/chat/knowledge-planet/weekly/feedback", { helpful });
}

export async function putKnowledgePlanetLearningGoal(goal: string): Promise<void> {
  await http.put("/open/v1/chat/knowledge-planet/learning-goal", { goal });
}
