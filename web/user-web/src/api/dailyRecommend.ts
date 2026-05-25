import { http } from "../plugins/http";

export type DailyRecommendItem = {
  id: string;
  tag: string;
  title: string;
  summary: string;
  source: string;
  date: string;
  url: string;
};

export type DailyRecommendResponse = {
  cacheKey: string;
  recommendDate: string;
  status: "OK" | "ERROR" | "LOADING" | "EMPTY";
  list: DailyRecommendItem[];
  profileTags: string[];
  errorMessage: string | null;
  retryAllowed: boolean;
  refreshAllowed: boolean;
};

export type DailyRecommendClickPayload = {
  itemId: string;
  tag?: string;
  title: string;
  summary?: string;
  source?: string;
  date?: string;
  url?: string;
};

export async function fetchDailyRecommend(): Promise<DailyRecommendResponse> {
  const { data } = await http.get<DailyRecommendResponse>("/open/v1/chat/daily-recommend", {
    timeout: 30000,
  });
  return data;
}

export async function retryDailyRecommend(): Promise<DailyRecommendResponse> {
  const { data } = await http.post<DailyRecommendResponse>("/open/v1/chat/daily-recommend/retry", null, {
    timeout: 30000,
  });
  return data;
}

/** 登录后强制按用户画像重新生成当日推荐。 */
export async function regenerateDailyRecommendOnLogin(): Promise<DailyRecommendResponse> {
  const { data } = await http.post<DailyRecommendResponse>(
    "/open/v1/chat/daily-recommend/regenerate-on-login",
    null,
    { timeout: 30000 },
  );
  return data;
}

/** 点击埋点：失败静默，不阻断跳转。 */
export function trackDailyRecommendClick(item: DailyRecommendClickPayload): void {
  void http
    .post("/open/v1/chat/daily-recommend/click", item, { timeout: 8000 })
    .catch(() => {
      /* 无感 */
    });
}
