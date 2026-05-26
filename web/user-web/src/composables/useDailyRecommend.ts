import { onMounted, readonly, ref } from "vue";
import type { DailyRecommendItem, DailyRecommendResponse } from "../api/dailyRecommend";
import {
  fetchDailyRecommend,
  regenerateDailyRecommendOnLogin,
  retryDailyRecommend,
} from "../api/dailyRecommend";
import { getUserAccessToken } from "../plugins/http";
import { apiRequestErrorMessage } from "../utils/apiRequestErrorMessage";
import { normalizeExternalUrl } from "../utils/externalUrl";

const LS_PREFIX = "daily-recommend-";
const LS_META_KEY = "daily-recommend-meta";

const PROFILE_POLL_INTERVAL_MS = 2000;
const PROFILE_POLL_MAX_ATTEMPTS = 60;

type CachedPayload = {
  cacheKey: string;
  recommendDate: string;
  recommendations: DailyRecommendItem[];
  profileTags: string[];
};

type RecommendMeta = {
  cacheKey: string;
  recommendDate: string;
};

/** 用户选中、待发送的推荐问句（埋点与发送后清除）。 */
export type SelectedStarterPrompt = {
  text: string;
  promptId: number | null;
  scene: "EMPTY" | "FOLLOW_UP";
};

function storageKey(cacheKey: string, recommendDate: string): string {
  return `${LS_PREFIX}${cacheKey}-${recommendDate}`;
}

function todayLocalDateStr(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function isSameCalendarDay(dateStr: string): boolean {
  return dateStr === todayLocalDateStr();
}

function readLastMeta(): RecommendMeta | null {
  try {
    const raw = localStorage.getItem(LS_META_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as RecommendMeta;
    if (!parsed.cacheKey || !parsed.recommendDate) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeLastMeta(cacheKey: string, recommendDate: string): void {
  localStorage.setItem(LS_META_KEY, JSON.stringify({ cacheKey, recommendDate }));
}

/** 清除当日推荐本地缓存（登录切换主体时须调用）。 */
export function clearDailyRecommendStorage(): void {
  localStorage.removeItem(LS_META_KEY);
  const keys: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k?.startsWith(LS_PREFIX)) keys.push(k);
  }
  for (const k of keys) localStorage.removeItem(k);
}

function readCache(cacheKey: string, recommendDate: string): CachedPayload | null {
  try {
    const raw = localStorage.getItem(storageKey(cacheKey, recommendDate));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CachedPayload;
    if (parsed.recommendDate !== recommendDate || !Array.isArray(parsed.recommendations)) {
      return null;
    }
    if (parsed.recommendations.length === 0) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeCache(
  cacheKey: string,
  recommendDate: string,
  items: DailyRecommendItem[],
  profileTags: string[],
): void {
  const payload: CachedPayload = { cacheKey, recommendDate, recommendations: items, profileTags };
  localStorage.setItem(storageKey(cacheKey, recommendDate), JSON.stringify(payload));
}

function normalizeRecommendItems(items: DailyRecommendItem[]): DailyRecommendItem[] {
  return items.map((it) => ({
    ...it,
    url: normalizeExternalUrl(it.url),
  }));
}

function tagsFromItems(items: DailyRecommendItem[]): string[] {
  const set = new Set<string>();
  for (const it of items) {
    const t = it.tag?.trim();
    if (t && t !== "—") set.add(t);
  }
  return set.size > 0 ? [...set] : ["热点", "资讯"];
}

export type DailyRecommendUiStatus = "loading" | "success" | "error" | "empty";

function createDailyRecommendStore() {
  const status = ref<DailyRecommendUiStatus>("loading");
  const recommendations = ref<DailyRecommendItem[]>([]);
  const profileTags = ref<string[]>([]);
  const errorMessage = ref<string | null>(null);
  const retryAllowed = ref(false);
  const refreshAllowed = ref(false);
  const recommendDate = ref("");
  const cacheKey = ref("");
  const retrying = ref(false);
  const fromTodayCache = ref(false);
  const selectedStarterPrompt = ref<SelectedStarterPrompt | null>(null);

  function applyProfileResponse(res: DailyRecommendResponse): boolean {
    cacheKey.value = res.cacheKey ?? "";
    recommendDate.value = res.recommendDate ?? "";
    retryAllowed.value = !!res.retryAllowed;
    refreshAllowed.value = !!res.refreshAllowed;
    profileTags.value =
      res.profileTags?.length ? res.profileTags : tagsFromItems(res.list ?? []);

    if (res.status === "LOADING") {
      status.value = "loading";
      errorMessage.value = null;
      return true;
    }
    if (res.status === "OK" && res.list.length > 0) {
      recommendations.value = normalizeRecommendItems(res.list);
      status.value = "success";
      errorMessage.value = null;
      fromTodayCache.value = false;
      if (res.cacheKey && res.recommendDate) {
        writeCache(res.cacheKey, res.recommendDate, res.list, profileTags.value);
        writeLastMeta(res.cacheKey, res.recommendDate);
      }
      return false;
    }
    if (res.status === "OK" && res.list.length === 0) {
      recommendations.value = [];
      status.value = "empty";
      errorMessage.value = res.errorMessage;
      return false;
    }
    if (res.status === "ERROR") {
      recommendations.value = [];
      status.value = "error";
      errorMessage.value = res.errorMessage ?? null;
      return false;
    }
    if (res.status === "EMPTY") {
      recommendations.value = [];
      status.value = "empty";
      errorMessage.value = res.errorMessage;
      return false;
    }
    status.value = "loading";
    return true;
  }

  async function pollProfileUntilReady(): Promise<void> {
    for (let i = 0; i < PROFILE_POLL_MAX_ATTEMPTS; i++) {
      await new Promise((r) => setTimeout(r, PROFILE_POLL_INTERVAL_MS));
      try {
        const res = await fetchDailyRecommend();
        const stillLoading = applyProfileResponse(res);
        if (!stillLoading) {
          if (res.cacheKey && res.recommendDate) {
            writeLastMeta(res.cacheKey, res.recommendDate);
          }
          return;
        }
      } catch (e) {
        recommendations.value = [];
        status.value = "error";
        errorMessage.value = apiRequestErrorMessage(e, "加载失败，请稍后重试");
        retryAllowed.value = true;
        refreshAllowed.value = true;
        return;
      }
    }
    status.value = "error";
    errorMessage.value = "画像推荐生成超时，请稍后重试";
    retryAllowed.value = true;
    refreshAllowed.value = true;
  }

  async function fetchProfileOnce(): Promise<void> {
    try {
      const res = await fetchDailyRecommend();
      const stillLoading = applyProfileResponse(res);
      if (stillLoading) {
        await pollProfileUntilReady();
        return;
      }
      if (res.cacheKey && res.recommendDate) {
        writeLastMeta(res.cacheKey, res.recommendDate);
      }
    } catch (e) {
      recommendations.value = [];
      status.value = "error";
      errorMessage.value = apiRequestErrorMessage(e, "加载失败，请稍后重试");
      retryAllowed.value = true;
      refreshAllowed.value = true;
    }
  }

  function setSelectedStarterPrompt(prompt: SelectedStarterPrompt): void {
    selectedStarterPrompt.value = { ...prompt };
  }

  function clearSelectedStarterPrompt(): void {
    selectedStarterPrompt.value = null;
  }

  /** 发送内容与待发送推荐问句一致时清除选中态。 */
  function takeSentStarterPrompt(sentText: string): boolean {
    const pending = selectedStarterPrompt.value;
    if (!pending || pending.text.trim() !== sentText.trim()) {
      return false;
    }
    selectedStarterPrompt.value = null;
    return true;
  }

  async function loadProfileFromApi(): Promise<void> {
    fromTodayCache.value = false;
    status.value = "loading";
    await fetchProfileOnce();
  }

  async function bootstrap(): Promise<void> {
    selectedStarterPrompt.value = null;

    const meta = readLastMeta();
    if (meta && isSameCalendarDay(meta.recommendDate)) {
      const cached = readCache(meta.cacheKey, meta.recommendDate);
      if (cached) {
        cacheKey.value = meta.cacheKey;
        recommendDate.value = meta.recommendDate;
        recommendations.value = normalizeRecommendItems(cached.recommendations);
        profileTags.value =
          cached.profileTags?.length ? cached.profileTags : tagsFromItems(cached.recommendations);
        status.value = "success";
        errorMessage.value = null;
        fromTodayCache.value = true;
        refreshAllowed.value = false;
        retryAllowed.value = false;
        return;
      }
    }

    status.value = "loading";
    await fetchProfileOnce();
  }

  async function retry(): Promise<void> {
    if (retrying.value || !retryAllowed.value) return;
    retrying.value = true;
    fromTodayCache.value = false;
    status.value = "loading";
    try {
      const res = await retryDailyRecommend();
      const stillLoading = applyProfileResponse(res);
      if (stillLoading) {
        await pollProfileUntilReady();
      }
    } catch (e) {
      status.value = "error";
      errorMessage.value = apiRequestErrorMessage(e, "加载失败，请稍后重试");
      retryAllowed.value = false;
      refreshAllowed.value = false;
    } finally {
      retrying.value = false;
    }
  }

  async function refresh(): Promise<"ok" | "noop" | "error"> {
    if (retryAllowed.value) {
      await retry();
      return status.value === "success" ? "ok" : "error";
    }
    if (fromTodayCache.value || status.value === "success") {
      return "noop";
    }
    await loadProfileFromApi();
    return recommendations.value.length > 0 ? "ok" : "error";
  }

  async function reloadAfterLogin(): Promise<void> {
    if (!getUserAccessToken()) return;
    clearDailyRecommendStorage();
    fromTodayCache.value = false;
    selectedStarterPrompt.value = null;
    status.value = "loading";
    try {
      const res = await regenerateDailyRecommendOnLogin();
      const stillLoading = applyProfileResponse(res);
      if (stillLoading) {
        await pollProfileUntilReady();
        return;
      }
      if (res.cacheKey && res.recommendDate) {
        writeLastMeta(res.cacheKey, res.recommendDate);
      }
    } catch (e) {
      recommendations.value = [];
      status.value = "error";
      errorMessage.value = apiRequestErrorMessage(e, "加载失败，请稍后重试");
      retryAllowed.value = true;
      refreshAllowed.value = true;
    }
  }

  /** 切换会话 / 新建对话：清除待发送推荐问句选中态。 */
  function syncForConversation(): void {
    selectedStarterPrompt.value = null;
  }

  onMounted(() => {
    void bootstrap();
  });

  return {
    status,
    recommendations,
    profileTags,
    errorMessage,
    retryAllowed,
    refreshAllowed,
    recommendDate,
    retrying,
    fromTodayCache,
    selectedStarterPrompt: readonly(selectedStarterPrompt),
    retry,
    refresh,
    reload: loadProfileFromApi,
    reloadAfterLogin,
    syncForConversation,
    setSelectedStarterPrompt,
    clearSelectedStarterPrompt,
    takeSentStarterPrompt,
  };
}

let storeInstance: ReturnType<typeof createDailyRecommendStore> | null = null;

export function useDailyRecommend() {
  if (!storeInstance) {
    storeInstance = createDailyRecommendStore();
  }
  return storeInstance;
}
