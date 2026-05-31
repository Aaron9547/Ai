<template>
  <div class="page dash-page">
    <div class="dash-wrap">
      <header class="dash-hero">
        <div class="dash-hero-text">
          <h1 class="dash-h1">{{ t("views.dashboard.title") }}</h1>
          <p class="dash-desc">{{ t("views.dashboard.sub") }}</p>
        </div>
        <div class="dash-hero-actions">
          <span v-if="summary" class="dash-time">{{ t("views.dashboard.updatedAt", { at: summary.generatedAt }) }}</span>
          <el-button type="primary" :loading="loading" @click="reload">{{ t("views.dashboard.refresh") }}</el-button>
        </div>
      </header>

      <el-alert v-if="err" type="error" :closable="false" show-icon :title="err" class="dash-err" />
      <el-skeleton v-else-if="loading && !summary" :rows="5" animated class="dash-skel" />

      <template v-else-if="summary">
        <div
          class="dash-data"
          v-loading="loading && !!summary"
          :element-loading-text="t('views.dashboard.reloadingOverlay')"
        >
          <DashboardKpiBlock
            :key="kpiAnimKey"
            :kpi="summary.kpi"
            :recent24h="summary.recent24h"
          />

        <section class="dash-sec">
          <el-row :gutter="18" class="dash-row dash-row--charts">
            <el-col :xs="24" :lg="8">
              <el-card shadow="never" class="chart-card">
                <template #header>
                  <span class="chart-card-title">{{ t("views.dashboard.chartHttpDaily") }}</span>
                </template>
                <div ref="chartAccessRef" class="chart-host" />
              </el-card>
            </el-col>
            <el-col :xs="24" :lg="8">
              <el-card shadow="never" class="chart-card">
                <template #header>
                  <div class="chart-card-header">
                    <span class="chart-card-title">{{ t("views.dashboard.chartMeterTokensDaily") }}</span>
                    <el-checkbox-group v-model="tokenSeriesVisible" size="small" class="token-series-toggle">
                      <el-checkbox label="prompt">{{ t("views.dashboard.tokenSeriesTogglePrompt") }}</el-checkbox>
                      <el-checkbox label="completion">{{ t("views.dashboard.tokenSeriesToggleCompletion") }}</el-checkbox>
                    </el-checkbox-group>
                  </div>
                </template>
                <div ref="chartMeterTokensRef" class="chart-host" />
              </el-card>
            </el-col>
            <el-col :xs="24" :lg="8">
              <el-card shadow="never" class="chart-card">
                <template #header>
                  <span class="chart-card-title">{{ t("views.dashboard.chartMeterCntDaily") }}</span>
                </template>
                <div ref="chartMeterCntRef" class="chart-host" />
              </el-card>
            </el-col>
          </el-row>
          <el-row :gutter="18" class="dash-row dash-row--charts">
            <el-col :span="24">
              <el-card shadow="never" class="chart-card">
                <template #header>
                  <div class="chart-card-header">
                    <span class="chart-card-title">{{ t("views.dashboard.chartMeterModelTrend") }}</span>
                    <div class="chart-card-header-actions">
                      <el-radio-group v-model="modelTokenTrendDays" size="small" class="model-trend-range">
                        <el-radio-button :label="7">{{ t("views.dashboard.modelTrendRange7d") }}</el-radio-button>
                        <el-radio-button :label="14">{{ t("views.dashboard.modelTrendRange14d") }}</el-radio-button>
                        <el-radio-button :label="30">{{ t("views.dashboard.modelTrendRange30d") }}</el-radio-button>
                      </el-radio-group>
                      <el-checkbox-group v-model="tokenSeriesVisible" size="small" class="token-series-toggle">
                        <el-checkbox label="prompt">{{ t("views.dashboard.tokenSeriesTogglePrompt") }}</el-checkbox>
                        <el-checkbox label="completion">{{ t("views.dashboard.tokenSeriesToggleCompletion") }}</el-checkbox>
                      </el-checkbox-group>
                    </div>
                  </div>
                </template>
                <p class="chart-card-hint">{{ t("views.dashboard.chartMeterModelTrendHint") }}</p>
                <div ref="chartMeterModelRef" class="chart-host chart-host--tall" />
              </el-card>
            </el-col>
          </el-row>
        </section>

        <section class="dash-sec">
          <h2 class="dash-sec-title">{{ t("views.dashboard.sectionUserGeo") }}</h2>
          <el-alert
            v-if="memberMapGeoAlert"
            type="warning"
            :closable="false"
            show-icon
            class="dash-map-err"
            :title="memberMapGeoAlert"
          />
          <el-alert
            v-else-if="memberRegionMapEmptyHint"
            type="info"
            :closable="false"
            show-icon
            class="dash-map-err"
            :title="memberRegionMapEmptyHint"
          />
          <el-row :gutter="18" class="dash-row dash-row--charts">
            <el-col :xs="24" :lg="14">
              <el-card shadow="never" class="chart-card chart-card--map">
                <template #header>
                  <div class="chart-card-header chart-card-header--map">
                    <span class="chart-card-title">{{ memberRegionCardTitle }}</span>
                    <el-radio-group v-model="memberMapMode" size="small" class="member-map-mode">
                      <el-radio-button label="world">{{ t("views.dashboard.mapModeWorld") }}</el-radio-button>
                      <el-radio-button label="china">{{ t("views.dashboard.mapModeChina") }}</el-radio-button>
                    </el-radio-group>
                  </div>
                </template>
                <div ref="chartWorldRef" class="chart-host chart-host--map" />
                <p v-if="memberMapMode === 'china' && chinaMapFootnote" class="dash-map-china-note">{{ chinaMapFootnote }}</p>
              </el-card>
            </el-col>
            <el-col :xs="24" :lg="10">
              <el-card shadow="never" class="chart-card chart-card--map">
                <template #header>
                  <span class="chart-card-title">{{ t("views.dashboard.chartLoginIpTop7d") }}</span>
                </template>
                <div ref="chartIpBarRef" class="chart-host chart-host--map" />
              </el-card>
            </el-col>
          </el-row>
        </section>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useRoute } from "vue-router";
import * as echarts from "echarts";
import type { ECharts, EChartsOption } from "echarts";
import * as adminDashboardApi from "@/api/adminDashboard";
import { AI_ADMIN_WORKSPACE_CHANGED_EVENT } from "@/constants/adminWorkspace";
import DashboardKpiBlock from "@/views/dashboard/DashboardKpiBlock.vue";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const { t, locale } = useI18n();
const route = useRoute();
const { isDark } = storeToRefs(useUiPreferencesStore());

const loading = ref(false);
const err = ref("");
const summary = ref<adminDashboardApi.AdminDashboardSummary | null>(null);
const kpiAnimKey = ref(0);
const memberMapMode = ref<"world" | "china">("world");
const worldMapLoadFailed = ref(false);
const chinaMapLoadFailed = ref(false);
const chinaMapAggMeta = ref({ cnOnly: 0, cnUnmatched: 0 });

const chartAccessRef = ref<HTMLDivElement | null>(null);
const chartMeterTokensRef = ref<HTMLDivElement | null>(null);
const chartMeterModelRef = ref<HTMLDivElement | null>(null);
const chartMeterCntRef = ref<HTMLDivElement | null>(null);
const tokenSeriesVisible = ref<Array<"prompt" | "completion">>(["prompt", "completion"]);
const modelTokenTrendDays = ref<7 | 14 | 30>(7);
const chartWorldRef = ref<HTMLDivElement | null>(null);
const chartIpBarRef = ref<HTMLDivElement | null>(null);

let chartAccess: ECharts | null = null;
let chartMeterTokens: ECharts | null = null;
let chartMeterModel: ECharts | null = null;
let chartMeterCnt: ECharts | null = null;
let chartWorld: ECharts | null = null;
let chartIpBar: ECharts | null = null;

const WORLD_GEO_URL = "https://cdn.jsdelivr.net/npm/echarts@4.9.0/map/json/world.json";
let worldMapRegistered = false;

/** ISO 3166-1 alpha-2 → ECharts 4 world.json 中的国家/地区名（未覆盖的码不会出现在地图上）。 */
const ISO2_TO_WORLD: Record<string, string> = {
  CN: "China",
  US: "United States",
  GB: "United Kingdom",
  DE: "Germany",
  FR: "France",
  JP: "Japan",
  IN: "India",
  BR: "Brazil",
  RU: "Russia",
  KR: "Korea",
  IT: "Italy",
  ES: "Spain",
  AU: "Australia",
  CA: "Canada",
  MX: "Mexico",
  NL: "Netherlands",
  SE: "Sweden",
  CH: "Switzerland",
  PL: "Poland",
  ID: "Indonesia",
  TR: "Turkey",
  SA: "Saudi Arabia",
  ZA: "South Africa",
  AR: "Argentina",
  EG: "Egypt",
  TH: "Thailand",
  VN: "Vietnam",
  PH: "Philippines",
  MY: "Malaysia",
  SG: "Singapore",
  TW: "Taiwan",
  HK: "Hong Kong",
  MO: "Macau",
  UA: "Ukraine",
  PT: "Portugal",
  BE: "Belgium",
  AT: "Austria",
  NO: "Norway",
  FI: "Finland",
  NZ: "New Zealand",
  IE: "Ireland",
  IL: "Israel",
  AE: "United Arab Emirates",
  DK: "Denmark",
  CZ: "Czech Rep.",
  RO: "Romania",
  HU: "Hungary",
  GR: "Greece",
};

const CHINA_GEO_URL = "https://cdn.jsdelivr.net/npm/echarts@4.9.0/map/json/china.json";
let chinaMapRegistered = false;

/** 与 echarts@4.9.0 map/json/china.json 中 properties.name 一致 */
const CN_EC_PROVINCE_NAMES = new Set([
  "上海",
  "云南",
  "内蒙古",
  "北京",
  "台湾",
  "吉林",
  "四川",
  "天津",
  "宁夏",
  "安徽",
  "山东",
  "山西",
  "广东",
  "广西",
  "新疆",
  "江苏",
  "江西",
  "河北",
  "河南",
  "浙江",
  "海南",
  "湖北",
  "湖南",
  "澳门",
  "甘肃",
  "福建",
  "西藏",
  "贵州",
  "辽宁",
  "重庆",
  "陕西",
  "青海",
  "香港",
  "黑龙江",
]);

/** Cloudflare / 英文州省描述 → 地图省名 */
const CN_REGION_EN_TO_ZH: Record<string, string> = {
  anhui: "安徽",
  beijing: "北京",
  chongqing: "重庆",
  fujian: "福建",
  gansu: "甘肃",
  guangdong: "广东",
  guangxi: "广西",
  guizhou: "贵州",
  hainan: "海南",
  hebei: "河北",
  henan: "河南",
  heilongjiang: "黑龙江",
  hubei: "湖北",
  hunan: "湖南",
  jiangsu: "江苏",
  jiangxi: "江西",
  jilin: "吉林",
  liaoning: "辽宁",
  ningxia: "宁夏",
  qinghai: "青海",
  shaanxi: "陕西",
  shanxi: "山西",
  shandong: "山东",
  shanghai: "上海",
  sichuan: "四川",
  taiwan: "台湾",
  tianjin: "天津",
  tibet: "西藏",
  xizang: "西藏",
  xinjiang: "新疆",
  yunnan: "云南",
  zhejiang: "浙江",
  "inner mongolia": "内蒙古",
  innermongolia: "内蒙古",
  "nei mongol": "内蒙古",
  "hong kong": "香港",
  hongkong: "香港",
  macao: "澳门",
  macau: "澳门",
};

function stripEnRegionSuffixes(raw: string): string {
  return raw
    .trim()
    .replace(/\s+zhuang autonomous region$/i, "")
    .replace(/\s+uygur autonomous region$/i, "")
    .replace(/\s+hui autonomous region$/i, "")
    .replace(/\s+autonomous region$/i, "")
    .replace(/\s+autonomous prefecture$/i, "")
    .replace(/\s+special administrative region$/i, "")
    .replace(/\s+province$/i, "")
    .replace(/\s+municipality$/i, "")
    .trim();
}

function normalizeCnRegionToProvince(regionRaw: string): string | null {
  const s = regionRaw.trim();
  if (!s) return null;
  if (CN_EC_PROVINCE_NAMES.has(s)) return s;

  const municipals: Record<string, string> = {
    北京市: "北京",
    天津市: "天津",
    上海市: "上海",
    重庆市: "重庆",
  };
  if (municipals[s]) return municipals[s];

  let zh = s
    .replace(/维吾尔自治区$/u, "")
    .replace(/壮族自治区$/u, "")
    .replace(/回族自治区$/u, "")
    .replace(/自治区$/u, "")
    .replace(/省$/u, "")
    .replace(/市$/u, "");
  if (CN_EC_PROVINCE_NAMES.has(zh)) return zh;
  if (zh.includes("内蒙古")) return "内蒙古";
  if (zh.includes("新疆")) return "新疆";
  if (zh.includes("西藏")) return "西藏";
  if (zh.includes("广西")) return "广西";
  if (zh.includes("宁夏")) return "宁夏";

  const stripped = stripEnRegionSuffixes(s).toLowerCase().replace(/\s+/g, " ");
  if (CN_REGION_EN_TO_ZH[stripped]) return CN_REGION_EN_TO_ZH[stripped];
  const first = stripped.split(/\s+/)[0] ?? "";
  if (first && CN_REGION_EN_TO_ZH[first]) return CN_REGION_EN_TO_ZH[first];
  return null;
}

function aggregateMemberWorldMapData(
  rows: NonNullable<adminDashboardApi.AdminDashboardSummary["memberLoginRegionCounts"]>,
): { name: string; value: number }[] {
  const byWorld = new Map<string, number>();
  for (const row of rows) {
    const raw = (row.name || "").trim();
    if (!raw || raw === "—") continue;
    const iso2 = raw.split("|")[0]?.trim().toUpperCase() ?? "";
    if (iso2.length !== 2) continue;
    const w = ISO2_TO_WORLD[iso2];
    if (!w) continue;
    byWorld.set(w, (byWorld.get(w) ?? 0) + row.value);
  }
  return [...byWorld.entries()].map(([name, value]) => ({ name, value }));
}

function aggregateMemberChinaProvinceData(
  rows: NonNullable<adminDashboardApi.AdminDashboardSummary["memberLoginRegionCounts"]>,
): { data: { name: string; value: number }[]; cnOnly: number; cnUnmatched: number } {
  const byProv = new Map<string, number>();
  let cnOnly = 0;
  let cnUnmatched = 0;
  for (const row of rows) {
    const raw = (row.name || "").trim();
    if (!raw || raw === "—") continue;
    const parts = raw.split("|").map((p) => p.trim());
    const iso = (parts[0] || "").toUpperCase();
    if (iso !== "CN") continue;
    if (parts.length < 2 || !parts[1]) {
      cnOnly += row.value;
      continue;
    }
    const prov = normalizeCnRegionToProvince(parts[1]);
    if (!prov) {
      cnUnmatched += row.value;
      continue;
    }
    byProv.set(prov, (byProv.get(prov) ?? 0) + row.value);
  }
  const data = [...byProv.entries()].map(([name, value]) => ({ name, value }));
  return { data, cnOnly, cnUnmatched };
}

const memberMapGeoAlert = computed(() => {
  if (memberMapMode.value === "world" && worldMapLoadFailed.value) {
    return t("views.dashboard.mapGeoLoadFailed");
  }
  if (memberMapMode.value === "china" && chinaMapLoadFailed.value) {
    return t("views.dashboard.mapChinaGeoLoadFailed");
  }
  return "";
});

const memberRegionMapEmptyHint = computed(() => {
  if (memberMapGeoAlert.value) return "";
  const rows = summary.value?.memberLoginRegionCounts ?? [];
  if (rows.length === 0) return t("views.dashboard.mapMemberRegionNoData");
  const worldData = aggregateMemberWorldMapData(rows);
  const chinaAgg = aggregateMemberChinaProvinceData(rows);
  const drawable =
    memberMapMode.value === "world" ? worldData.length > 0 : chinaAgg.data.length > 0;
  if (drawable) return "";
  const unknown = rows.find((r) => (r.name || "").trim() === "—")?.value ?? 0;
  const total = rows.reduce((s, r) => s + r.value, 0);
  if (unknown > 0 && unknown >= total) {
    return t("views.dashboard.mapMemberRegionAllUnknown");
  }
  return t("views.dashboard.mapMemberRegionUnmapped");
});

const memberRegionCardTitle = computed(() =>
  memberMapMode.value === "world"
    ? t("views.dashboard.chartMemberRegionWorld")
    : t("views.dashboard.chartMemberRegionChina"),
);

const chinaMapFootnote = computed(() => {
  const { cnOnly, cnUnmatched } = chinaMapAggMeta.value;
  if (cnOnly + cnUnmatched <= 0) return "";
  return t("views.dashboard.mapChinaFootnote", { only: cnOnly, unknown: cnUnmatched });
});

function disposeCharts() {
  chartAccess?.dispose();
  chartMeterTokens?.dispose();
  chartMeterModel?.dispose();
  chartMeterCnt?.dispose();
  chartWorld?.dispose();
  chartIpBar?.dispose();
  chartAccess = chartMeterTokens = chartMeterModel = chartMeterCnt = chartWorld = chartIpBar = null;
}

function cssVar(name: string, fallback: string): string {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return raw || fallback;
}

function chartPalette() {
  return {
    axis: cssVar("--el-text-color-secondary", "#909399"),
    split: cssVar("--el-border-color-lighter", "#ebeef5"),
    axisLine: cssVar("--el-border-color", "#dcdfe6"),
    /** 与 tooltip 文字同色变量配套，避免深色模式下默认浅色浮层 + 浅色字不可读 */
    tooltipBg: cssVar("--el-bg-color", "#ffffff"),
    tooltipBorder: cssVar("--el-border-color", "#dcdfe6"),
    tooltipText: cssVar("--el-text-color-primary", "#303133"),
    symbolBorder: cssVar("--el-bg-color", "#ffffff"),
    primary: cssVar("--el-color-primary", "#409eff"),
    series2: cssVar("--el-color-warning", "#e6a23c"),
    series3: cssVar("--el-color-success", "#67c23a"),
  };
}

async function ensureWorldMap(): Promise<boolean> {
  if (worldMapRegistered) return true;
  try {
    const res = await fetch(WORLD_GEO_URL, { mode: "cors" });
    if (!res.ok) return false;
    const geoJson = (await res.json()) as Record<string, unknown>;
    echarts.registerMap("world", geoJson as never);
    worldMapRegistered = true;
    return true;
  } catch {
    return false;
  }
}

function buildWorldMapOption(data: { name: string; value: number }[], pal: ReturnType<typeof chartPalette>): EChartsOption {
  const max = Math.max(1, ...data.map((d) => d.value), 1);
  return {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "item",
      backgroundColor: pal.tooltipBg,
      borderColor: pal.tooltipBorder,
      textStyle: { color: pal.tooltipText, fontSize: 12 },
    },
    visualMap: {
      min: 0,
      max,
      calculable: true,
      inRange: { color: ["#dbeafe", "#1d4ed8"] },
      textStyle: { color: pal.axis, fontSize: 11 },
    },
    series: [
      {
        name: t("views.dashboard.seriesMemberCount"),
        type: "map",
        roam: true,
        map: "world",
        emphasis: {
          label: { show: true, fontSize: 11 },
          itemStyle: { areaColor: pal.series2 },
        },
        data,
      },
    ],
  };
}

async function ensureChinaMap(): Promise<boolean> {
  if (chinaMapRegistered) return true;
  try {
    const res = await fetch(CHINA_GEO_URL, { mode: "cors" });
    if (!res.ok) return false;
    const geoJson = (await res.json()) as Record<string, unknown>;
    echarts.registerMap("china", geoJson as never);
    chinaMapRegistered = true;
    return true;
  } catch {
    return false;
  }
}

function buildChinaMapOption(data: { name: string; value: number }[], pal: ReturnType<typeof chartPalette>): EChartsOption {
  const max = Math.max(1, ...data.map((d) => d.value), 1);
  return {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "item",
      backgroundColor: pal.tooltipBg,
      borderColor: pal.tooltipBorder,
      textStyle: { color: pal.tooltipText, fontSize: 12 },
    },
    visualMap: {
      min: 0,
      max,
      calculable: true,
      inRange: { color: ["#dcfce7", "#15803d"] },
      textStyle: { color: pal.axis, fontSize: 11 },
    },
    series: [
      {
        name: t("views.dashboard.seriesMemberCount"),
        type: "map",
        roam: true,
        map: "china",
        emphasis: {
          label: { show: true, fontSize: 11 },
          itemStyle: { areaColor: pal.series2 },
        },
        data,
      },
    ],
  };
}

async function applyMemberRegionChart() {
  const s = summary.value;
  if (!s || !chartWorldRef.value) return;
  const pal = chartPalette();
  const rows = s.memberLoginRegionCounts ?? [];
  const worldData = aggregateMemberWorldMapData(rows);
  const chinaAgg = aggregateMemberChinaProvinceData(rows);
  chinaMapAggMeta.value = { cnOnly: chinaAgg.cnOnly, cnUnmatched: chinaAgg.cnUnmatched };

  if (!chartWorld) chartWorld = echarts.init(chartWorldRef.value);
  if (memberMapMode.value === "world") {
    if (!worldMapLoadFailed.value) {
      chartWorld.setOption(buildWorldMapOption(worldData, pal), true);
    } else {
      chartWorld.clear();
    }
  } else if (!chinaMapLoadFailed.value) {
    chartWorld.setOption(buildChinaMapOption(chinaAgg.data, pal), true);
  } else {
    chartWorld.clear();
  }
  chartWorld.resize();
}

function buildIpBarOption(
  rows: NonNullable<adminDashboardApi.AdminDashboardSummary["topMemberClientIpsLast7d"]>,
  pal: ReturnType<typeof chartPalette>,
): EChartsOption {
  const labels = rows.map((r) => r.clientIp).reverse();
  const hits = rows.map((r) => r.hits).reverse();
  const users = rows.map((r) => r.distinctUsers).reverse();
  return {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      backgroundColor: pal.tooltipBg,
      borderColor: pal.tooltipBorder,
      textStyle: { color: pal.tooltipText, fontSize: 12 },
      formatter: (p: unknown) => {
        const arr = Array.isArray(p) ? p : [p];
        const first = arr[0] as { name?: string; data?: number; value?: number } | undefined;
        const ip = first?.name ?? "";
        const idx = labels.indexOf(ip);
        const hit = idx >= 0 ? hits[idx] : first?.data ?? first?.value;
        const du = idx >= 0 ? users[idx] : 0;
        return `${ip}<br/>${t("views.dashboard.seriesHits")}: ${hit}<br/>${t("views.dashboard.seriesDistinctUsers")}: ${du}`;
      },
    },
    grid: { left: 132, right: 20, top: 12, bottom: 24 },
    xAxis: {
      type: "value",
      axisLabel: { color: pal.axis, fontSize: 11 },
      splitLine: { lineStyle: { color: pal.split, type: "dashed" } },
    },
    yAxis: {
      type: "category",
      data: labels,
      axisLabel: { color: pal.axis, fontSize: 11 },
      axisLine: { lineStyle: { color: pal.axisLine } },
      axisTick: { show: false },
    },
    series: [
      {
        name: t("views.dashboard.seriesHits"),
        type: "bar",
        data: hits,
        barMaxWidth: 18,
        itemStyle: { color: pal.primary, borderRadius: [0, 6, 6, 0] },
      },
    ],
  };
}

/** 折线图横轴：与后端北京时间日桶一致的展示。 */
function formatChartDayLabel(day: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(day || "").trim());
  if (!m) return day;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  if (String(locale.value).toLowerCase().startsWith("zh")) {
    return `${mo}月${d}日`;
  }
  const dt = new Date(y, mo - 1, d);
  return dt.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function tokenSeriesFlags() {
  const v = tokenSeriesVisible.value;
  return {
    prompt: v.includes("prompt"),
    completion: v.includes("completion"),
  };
}

function buildDualTokenLineOption(
  days: string[],
  promptVals: number[],
  completionVals: number[],
  showPrompt: boolean,
  showCompletion: boolean,
): EChartsOption {
  const th = chartPalette();
  const series: NonNullable<EChartsOption["series"]> = [];
  if (showPrompt) {
    series.push({
      name: t("views.dashboard.seriesPromptTokens"),
      type: "line",
      smooth: 0.45,
      symbol: "circle",
      symbolSize: 7,
      data: promptVals,
      lineStyle: { width: 3, color: th.primary },
      itemStyle: { color: th.primary, borderWidth: 2, borderColor: th.symbolBorder },
      areaStyle: { color: th.primary, opacity: 0.12 },
    });
  }
  if (showCompletion) {
    series.push({
      name: t("views.dashboard.seriesCompletionTokens"),
      type: "line",
      smooth: 0.45,
      symbol: "circle",
      symbolSize: 7,
      data: completionVals,
      lineStyle: { width: 3, color: th.series2 },
      itemStyle: { color: th.series2, borderWidth: 2, borderColor: th.symbolBorder },
      areaStyle: { color: th.series2, opacity: 0.12 },
    });
  }
  return {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "axis",
      backgroundColor: th.tooltipBg,
      borderColor: th.tooltipBorder,
      textStyle: { color: th.tooltipText, fontSize: 12 },
    },
    legend: series.length > 1 ? { top: 0, textStyle: { color: th.axis, fontSize: 11 } } : undefined,
    grid: { left: 44, right: 12, top: series.length > 1 ? 36 : 28, bottom: 36 },
    xAxis: {
      type: "category",
      data: days,
      axisLabel: {
        color: th.axis,
        fontSize: 11,
        rotate: days.length > 6 ? 30 : 0,
        formatter: (v: string) => formatChartDayLabel(v),
      },
      axisLine: { lineStyle: { color: th.axisLine } },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      axisLabel: { color: th.axis, fontSize: 11 },
      splitLine: { lineStyle: { color: th.split, type: "dashed" } },
    },
    series,
  };
}

type ModelTrendSlice = {
  modelAlias: string;
  daily: { day: string; promptTokens: number; completionTokens: number }[];
};

function sliceModelTrendByDays(models: ModelTrendSlice[], dayCount: number): ModelTrendSlice[] {
  if (dayCount <= 0) return models;
  return models.map((m) => ({
    modelAlias: m.modelAlias,
    daily: m.daily.slice(-dayCount),
  }));
}

function buildModelTokenTrendLineOption(
  models: ModelTrendSlice[],
  showPrompt: boolean,
  showCompletion: boolean,
): EChartsOption {
  const th = chartPalette();
  const modelColors = [th.primary, th.series2, th.series3];
  const days = models[0]?.daily.map((d) => d.day) ?? [];
  const series: NonNullable<EChartsOption["series"]> = [];

  models.forEach((m, idx) => {
    const color = modelColors[idx % modelColors.length];
    const promptVals = m.daily.map((d) => d.promptTokens);
    const completionVals = m.daily.map((d) => d.completionTokens);
    if (showPrompt) {
      series.push({
        name: `${m.modelAlias} ${t("views.dashboard.modelSeriesPromptSuffix")}`,
        type: "line",
        smooth: 0.45,
        symbol: "circle",
        symbolSize: 6,
        data: promptVals,
        lineStyle: { width: 2.5, color },
        itemStyle: { color, borderWidth: 2, borderColor: th.symbolBorder },
      });
    }
    if (showCompletion) {
      series.push({
        name: `${m.modelAlias} ${t("views.dashboard.modelSeriesCompletionSuffix")}`,
        type: "line",
        smooth: 0.45,
        symbol: "circle",
        symbolSize: 6,
        data: completionVals,
        lineStyle: { width: 2.5, color, type: "dashed" },
        itemStyle: { color, borderWidth: 2, borderColor: th.symbolBorder },
      });
    }
  });

  return {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "axis",
      backgroundColor: th.tooltipBg,
      borderColor: th.tooltipBorder,
      textStyle: { color: th.tooltipText, fontSize: 12 },
      formatter: (params: unknown) => {
        const arr = Array.isArray(params) ? params : [params];
        const first = arr[0] as { axisValue?: string; name?: string } | undefined;
        const day = first?.axisValue ?? first?.name ?? "";
        const lines = [day];
        for (const p of arr) {
          const item = p as { seriesName?: string; value?: number; data?: number };
          const v = item.value ?? item.data ?? 0;
          lines.push(`${item.seriesName ?? ""}: ${v}`);
        }
        return lines.join("<br/>");
      },
    },
    legend:
      series.length > 1
        ? { top: 0, type: "scroll", textStyle: { color: th.axis, fontSize: 11 } }
        : undefined,
    grid: { left: 44, right: 12, top: series.length > 1 ? 48 : 28, bottom: days.length > 10 ? 48 : 36 },
    xAxis: {
      type: "category",
      data: days,
      axisLabel: {
        color: th.axis,
        fontSize: 11,
        rotate: days.length > 10 ? 30 : days.length > 6 ? 22 : 0,
        formatter: (v: string) => formatChartDayLabel(v),
      },
      axisLine: { lineStyle: { color: th.axisLine } },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      axisLabel: { color: th.axis, fontSize: 11 },
      splitLine: { lineStyle: { color: th.split, type: "dashed" } },
    },
    series,
  };
}

function buildLineOption(seriesName: string, days: string[], values: number[], color: string): EChartsOption {
  const th = chartPalette();
  return {
    backgroundColor: "transparent",
    tooltip: {
      trigger: "axis",
      backgroundColor: th.tooltipBg,
      borderColor: th.tooltipBorder,
      textStyle: { color: th.tooltipText, fontSize: 12 },
    },
    grid: { left: 44, right: 12, top: 28, bottom: 36 },
    xAxis: {
      type: "category",
      data: days,
      axisLabel: {
        color: th.axis,
        fontSize: 11,
        rotate: days.length > 6 ? 30 : 0,
        formatter: (v: string) => formatChartDayLabel(v),
      },
      axisLine: { lineStyle: { color: th.axisLine } },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      axisLabel: { color: th.axis, fontSize: 11 },
      splitLine: { lineStyle: { color: th.split, type: "dashed" } },
    },
    series: [
      {
        name: seriesName,
        type: "line",
        smooth: 0.45,
        symbol: "circle",
        symbolSize: 7,
        data: values,
        lineStyle: { width: 3, color },
        itemStyle: { color, borderWidth: 2, borderColor: th.symbolBorder },
        areaStyle: { color, opacity: 0.14 },
      },
    ],
  };
}

function renderCharts() {
  const s = summary.value;
  if (!s) return;
  const httpByDay = s.httpAccessByDay ?? [];
  const tokensByDay = s.meteringTokensByDay ?? [];
  const eventsByDay = s.meteringEventsByDay ?? [];
  const days = httpByDay.map((d) => d.day);
  const accessVals = httpByDay.map((d) => d.count);
  const tokenDays = tokensByDay.map((d) => d.day);
  const promptByDay = tokensByDay.map((d) => d.promptTokens);
  const completionByDay = tokensByDay.map((d) => d.completionTokens);
  const cntVals = eventsByDay.map((d) => d.count);
  const { prompt: showPrompt, completion: showCompletion } = tokenSeriesFlags();

  const pal = chartPalette();
  if (chartAccessRef.value) {
    if (!chartAccess) chartAccess = echarts.init(chartAccessRef.value);
    chartAccess.setOption(buildLineOption(t("views.dashboard.seriesAccessCount"), days, accessVals, pal.primary));
  }
  if (chartMeterTokensRef.value) {
    if (!chartMeterTokens) chartMeterTokens = echarts.init(chartMeterTokensRef.value);
    chartMeterTokens.setOption(
      buildDualTokenLineOption(tokenDays, promptByDay, completionByDay, showPrompt, showCompletion),
      true,
    );
  }
  if (chartMeterCntRef.value) {
    if (!chartMeterCnt) chartMeterCnt = echarts.init(chartMeterCntRef.value);
    chartMeterCnt.setOption(buildLineOption(t("views.dashboard.seriesEventCount"), days, cntVals, pal.series3));
  }
  const trendModels = sliceModelTrendByDays(s.topModelTokenTrend30d ?? [], modelTokenTrendDays.value);
  if (chartMeterModelRef.value) {
    if (!chartMeterModel) chartMeterModel = echarts.init(chartMeterModelRef.value);
    if ((!showPrompt && !showCompletion) || trendModels.length === 0) {
      chartMeterModel.clear();
    } else {
      chartMeterModel.setOption(buildModelTokenTrendLineOption(trendModels, showPrompt, showCompletion), true);
    }
  }
  chartAccess?.resize();
  chartMeterTokens?.resize();
  chartMeterModel?.resize();
  chartMeterCnt?.resize();
}

async function renderGeoCharts() {
  const s = summary.value;
  if (!s) return;
  const [worldOk, chinaOk] = await Promise.all([ensureWorldMap(), ensureChinaMap()]);
  worldMapLoadFailed.value = !worldOk;
  chinaMapLoadFailed.value = !chinaOk;
  await nextTick();
  await applyMemberRegionChart();
  const pal = chartPalette();
  const ips = (s.topMemberClientIpsLast7d ?? []).slice(0, 15);
  if (chartIpBarRef.value) {
    if (!chartIpBar) chartIpBar = echarts.init(chartIpBarRef.value);
    if (ips.length === 0) {
      chartIpBar.clear();
    } else {
      chartIpBar.setOption(buildIpBarOption(ips, pal), true);
    }
    chartIpBar.resize();
  }
}

async function reload() {
  loading.value = true;
  err.value = "";
  try {
    summary.value = await adminDashboardApi.fetchDashboardSummary();
    kpiAnimKey.value += 1;
    await nextTick();
    try {
      renderCharts();
    } catch (chartErr: unknown) {
      console.error("dashboard charts render failed", chartErr);
    }
    await renderGeoCharts();
  } catch (e: unknown) {
    err.value = apiRequestErrorMessage(e, t("views.dashboard.loadFailed"));
    summary.value = null;
    disposeCharts();
  } finally {
    loading.value = false;
  }
}

function onResize() {
  chartAccess?.resize();
  chartMeterTokens?.resize();
  chartMeterModel?.resize();
  chartMeterCnt?.resize();
  chartWorld?.resize();
  chartIpBar?.resize();
}

watch([tokenSeriesVisible, modelTokenTrendDays], () => {
  if (summary.value) {
    renderCharts();
  }
});

watch(isDark, async () => {
  disposeCharts();
  await nextTick();
  if (summary.value) {
    renderCharts();
    await renderGeoCharts();
  }
});

watch(memberMapMode, async () => {
  await nextTick();
  if (summary.value) {
    await applyMemberRegionChart();
  }
});

function onWorkspaceChanged() {
  if (route.path === "/dashboard") {
    void reload();
  }
}

onMounted(() => {
  void reload();
  window.addEventListener(AI_ADMIN_WORKSPACE_CHANGED_EVENT, onWorkspaceChanged);
  window.addEventListener("resize", onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener(AI_ADMIN_WORKSPACE_CHANGED_EVENT, onWorkspaceChanged);
  window.removeEventListener("resize", onResize);
  disposeCharts();
});
</script>

<style scoped>
.dash-page {
  min-height: 0;
  background: var(--el-fill-color-extra-light);
}

.dash-wrap {
  width: 100%;
  max-width: 1580px;
  margin: 0 auto;
  padding: 16px clamp(12px, 2.5vw, 32px) 32px;
  box-sizing: border-box;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.dash-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px 20px;
  margin-bottom: 20px;
  padding: 22px 24px;
  border-radius: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.6) inset, 0 10px 40px rgba(15, 23, 42, 0.06);
}

.dash-h1 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.dash-desc {
  margin: 0;
  max-width: 52rem;
  font-size: 13px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.dash-hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.dash-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-extra-light);
}

.dash-map-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-secondary);
}

.dash-map-err {
  margin-bottom: 12px;
  border-radius: 12px;
}

.dash-err {
  border-radius: 12px;
  margin-bottom: 12px;
}

.dash-skel {
  margin-top: 8px;
}

.dash-sec {
  margin-bottom: 22px;
}

.dash-sec-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  letter-spacing: 0.04em;
}

.dash-row {
  width: 100%;
}

.dash-row--charts {
  margin-bottom: 0;
}

.dash-data {
  position: relative;
  min-height: 200px;
}

/* Charts */
.chart-card {
  border-radius: 16px;
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 14px;
  overflow: hidden;
  background: var(--el-bg-color);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 10px 28px rgba(15, 23, 42, 0.05);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.chart-card:hover {
  border-color: color-mix(in srgb, var(--el-color-primary) 22%, var(--el-border-color-lighter));
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06), 0 18px 44px rgba(15, 23, 42, 0.08);
}

.chart-card--map {
  margin-bottom: 14px;
}

.chart-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background: var(--el-bg-color);
}

.chart-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.chart-card-header,
.chart-card-header--map {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.chart-card-header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-left: auto;
}

.chart-card-header-actions .token-series-toggle {
  margin-left: 0;
}

.model-trend-range {
  flex-shrink: 0;
}

.token-series-toggle {
  flex-shrink: 0;
  margin-left: auto;
}

.chart-card-hint {
  margin: 0 0 8px;
  padding: 0 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.chart-host--tall {
  height: 300px;
}

.chart-card-header--map .chart-card-title {
  min-width: 0;
}

.member-map-mode {
  flex-shrink: 0;
  margin-left: auto;
}

.dash-map-china-note {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.chart-card :deep(.el-card__body) {
  padding: 8px 12px 14px;
}

.chart-host {
  width: 100%;
  height: 248px;
}

.chart-host--map {
  height: 336px;
}
</style>

<style>
/* 深色：图表卡片 hover 的 slate 投影在暗底上发灰，改为深色域阴影 */
html.dark .dash-page .dash-hero {
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.06) inset, 0 10px 40px rgba(0, 0, 0, 0.35);
}

html.dark .dash-page .chart-card {
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3), 0 8px 22px rgba(0, 0, 0, 0.22);
}

html.dark .dash-page .chart-card:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.07);
}
</style>
