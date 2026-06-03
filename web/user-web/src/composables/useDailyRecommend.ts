import { readonly, ref } from "vue";
import type { DailyRecommendItem, DailyRecommendResponse } from "../api/dailyRecommend";
import {
  fetchDailyRecommend,
  regenerateDailyRecommendOnLogin,
  retryDailyRecommend,
} from "../api/dailyRecommend";
import { getUserAccessToken } from "../plugins/http";
import { apiRequestErrorMessage } from "../utils/apiRequestErrorMessage";
import { normalizeExternalUrl } from "../utils/externalUrl";
import { scheduleIdle } from "../utils/scheduleIdle";

const LS_PREFIX = "daily-recommend-";
const LS_META_KEY = "daily-recommend-meta";

const PROFILE_POLL_INTERVAL_MS = 1200;
const PROFILE_POLL_INITIAL_MS = 450;
const PROFILE_POLL_MAX_ATTEMPTS = 60;
/** 跨日检测轮询（前台长挂兜底，与午夜定时互补） */
const DAY_ROLLOVER_POLL_MS = 5 * 60 * 1000;
/** 过本地 0 点后稍等再拉取，避免边界竞态 */
const DAY_ROLLOVER_MIDNIGHT_BUFFER_MS = 30_000;

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

/** 仅当已知批次日期且非今日时为跨日；空字符串不算「过期」，避免切回前台误进 loading。 */
function isRecommendDateStale(dateStr: string): boolean {
  if (!dateStr) return false;
  return !isSameCalendarDay(dateStr);
}

function msUntilNextLocalMidnight(): number {
  const now = new Date();
  const next = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 0);
  next.setTime(next.getTime() + DAY_ROLLOVER_MIDNIGHT_BUFFER_MS);
  return Math.max(1000, next.getTime() - now.getTime());
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

let dayRolloverWatchInitialized = false;
let dayRolloverPollTimer: number | null = null;
/** 过本地 0 点后标记待刷新；标签在后台时不打 API，回前台再只刷新今日洞察。 */
let pendingDayRolloverRefresh = false;
/** bootstrap / 跨日刷新完成后回调，用于按「是否已是今日」启停轮询 */
let syncDayRolloverPollState: () => void = () => {};

function purgeStaleRecommendLocalCache(): void {
  const meta = readLastMeta();
  if (!meta || isSameCalendarDay(meta.recommendDate)) {
    return;
  }
  localStorage.removeItem(storageKey(meta.cacheKey, meta.recommendDate));
  localStorage.removeItem(LS_META_KEY);
}

/** 仅当已有批次日期且非今日时才需轮询（空日期表示尚未 bootstrap，不启轮询） */
function shouldRunDayRolloverPoll(dateStr: string): boolean {
  return !!dateStr && isRecommendDateStale(dateStr);
}

function stopDayRolloverPoll(): void {
  if (dayRolloverPollTimer != null) {
    clearInterval(dayRolloverPollTimer);
    dayRolloverPollTimer = null;
  }
}

function syncDayRolloverPoll(
  getRecommendDate: () => string,
  refreshIfDateStale: () => Promise<void>,
): void {
  const dateStr = getRecommendDate() || readLastMeta()?.recommendDate || "";
  if (!shouldRunDayRolloverPoll(dateStr)) {
    stopDayRolloverPoll();
    return;
  }
  if (dayRolloverPollTimer != null) return;

  dayRolloverPollTimer = window.setInterval(() => {
    if (document.visibilityState !== "visible") {
      return;
    }
    const current = getRecommendDate() || readLastMeta()?.recommendDate || "";
    if (!shouldRunDayRolloverPoll(current)) {
      stopDayRolloverPoll();
      return;
    }
    void refreshIfDateStale().finally(() => syncDayRolloverPoll(getRecommendDate, refreshIfDateStale));
  }, DAY_ROLLOVER_POLL_MS);
}

function initDayRolloverAutoRefresh(
  getRecommendDate: () => string,
  onTabVisible: () => Promise<void>,
  refreshIfDateStale: () => Promise<void>,
): void {
  if (dayRolloverWatchInitialized) return;
  dayRolloverWatchInitialized = true;

  const syncPoll = (): void => syncDayRolloverPoll(getRecommendDate, refreshIfDateStale);
  syncDayRolloverPollState = syncPoll;

  const onVisibilityChange = (): void => {
    if (document.visibilityState === "visible") {
      void onTabVisible().finally(syncPoll);
    }
  };
  document.addEventListener("visibilitychange", onVisibilityChange);

  const scheduleMidnightCheck = (): void => {
    window.setTimeout(() => {
      pendingDayRolloverRefresh = true;
      if (document.visibilityState === "visible") {
        void onTabVisible().finally(syncPoll);
      }
      scheduleMidnightCheck();
    }, msUntilNextLocalMidnight());
  };
  scheduleMidnightCheck();
}

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
  let dayRolloverInFlight = false;

  type ApplyProfileOptions = { allowLoadingUi?: boolean };

  function applyProfileResponse(res: DailyRecommendResponse, options?: ApplyProfileOptions): boolean {
    const allowLoadingUi = options?.allowLoadingUi !== false;
    cacheKey.value = res.cacheKey ?? "";
    recommendDate.value = res.recommendDate ?? "";
    retryAllowed.value = !!res.retryAllowed;
    refreshAllowed.value = !!res.refreshAllowed;
    profileTags.value =
      res.profileTags?.length ? res.profileTags : tagsFromItems(res.list ?? []);

    if (res.status === "LOADING") {
      if (allowLoadingUi) {
        status.value = "loading";
        errorMessage.value = null;
      }
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

  async function pollProfileUntilReady(options?: ApplyProfileOptions): Promise<void> {
    const allowLoadingUi = options?.allowLoadingUi !== false;
    for (let i = 0; i < PROFILE_POLL_MAX_ATTEMPTS; i++) {
      await new Promise((r) =>
        setTimeout(r, i === 0 ? PROFILE_POLL_INITIAL_MS : PROFILE_POLL_INTERVAL_MS),
      );
      try {
        const res = await fetchDailyRecommend();
        const stillLoading = applyProfileResponse(res, options);
        if (!stillLoading) {
          if (res.cacheKey && res.recommendDate) {
            writeLastMeta(res.cacheKey, res.recommendDate);
          }
          return;
        }
      } catch (e) {
        if (!allowLoadingUi) {
          return;
        }
        recommendations.value = [];
        status.value = "error";
        errorMessage.value = apiRequestErrorMessage(e, "加载失败，请稍后重试");
        retryAllowed.value = true;
        refreshAllowed.value = true;
        return;
      }
    }
    try {
      const res = await fetchDailyRecommend();
      const stillLoading = applyProfileResponse(res, options);
      if (!stillLoading) {
        if (res.cacheKey && res.recommendDate) {
          writeLastMeta(res.cacheKey, res.recommendDate);
        }
        return;
      }
    } catch {
      /* 超时前最后一拉失败则走下方错误态 */
    }
    if (!allowLoadingUi) {
      return;
    }
    status.value = "error";
    errorMessage.value = "画像推荐生成超时，请稍后重试";
    retryAllowed.value = true;
    refreshAllowed.value = true;
  }

  async function fetchProfileOnce(options?: ApplyProfileOptions): Promise<void> {
    try {
      const res = await fetchDailyRecommend();
      const stillLoading = applyProfileResponse(res, options);
      if (stillLoading) {
        await pollProfileUntilReady(options);
        return;
      }
      if (res.cacheKey && res.recommendDate) {
        writeLastMeta(res.cacheKey, res.recommendDate);
      }
    } catch (e) {
      if (options?.allowLoadingUi === false) {
        return;
      }
      recommendations.value = [];
      status.value = "error";
      errorMessage.value = apiRequestErrorMessage(e, "加载失败，请稍后重试");
      retryAllowed.value = true;
      refreshAllowed.value = true;
    }
  }

  /** 本地缓存命中后静默与服务端对齐（域名/IP 不同源缓存不共享，以库表为准）。 */
  async function syncFromServerAfterCacheHit(): Promise<void> {
    await fetchProfileOnce({ allowLoadingUi: false });
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
        scheduleIdle(() => {
          void syncFromServerAfterCacheHit();
        });
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

  /** 跨日：仅今日洞察侧栏进入 loading 并拉新一批，不影响对话主区。 */
  async function refreshIfDateStale(): Promise<void> {
    if (dayRolloverInFlight || retrying.value || status.value === "loading") return;
    const dateStr = recommendDate.value || readLastMeta()?.recommendDate || "";
    if (!isRecommendDateStale(dateStr) && !pendingDayRolloverRefresh) return;

    dayRolloverInFlight = true;
    pendingDayRolloverRefresh = false;
    fromTodayCache.value = false;
    purgeStaleRecommendLocalCache();
    recommendations.value = [];
    status.value = "loading";
    try {
      await fetchProfileOnce();
    } finally {
      dayRolloverInFlight = false;
      syncDayRolloverPollState();
    }
  }

  /** 切回前台：仅跨日（或午夜待刷新）更新今日洞察；同日不请求、不动对话区。 */
  async function refreshOnTabVisible(): Promise<void> {
    if (dayRolloverInFlight || retrying.value || status.value === "loading") return;
    const dateStr = recommendDate.value || readLastMeta()?.recommendDate || "";
    if (pendingDayRolloverRefresh || isRecommendDateStale(dateStr)) {
      await refreshIfDateStale();
    }
  }

  function getRecommendDateStr(): string {
    return recommendDate.value;
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

  let bootstrapPromise: Promise<void> | null = null;

  /** 幂等：仅在画像推荐侧栏/入口挂载后拉取，避免 ChatView 首屏抢带宽。 */
  function ensureBootstrapped(): Promise<void> {
    if (!bootstrapPromise) {
      bootstrapPromise = bootstrap().finally(syncDayRolloverPollState);
    }
    return bootstrapPromise;
  }

  initDayRolloverAutoRefresh(getRecommendDateStr, refreshOnTabVisible, refreshIfDateStale);

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
    ensureBootstrapped,
  };
}

let storeInstance: ReturnType<typeof createDailyRecommendStore> | null = null;

export function useDailyRecommend() {
  if (!storeInstance) {
    storeInstance = createDailyRecommendStore();
  }
  return storeInstance;
}
