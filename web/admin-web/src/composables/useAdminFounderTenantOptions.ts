import { computed, onMounted, ref } from "vue";
import { listTenants, type TenantRow } from "../api/tenants";
import { AI_ADMIN_ACCESS_TOKEN_KEY } from "../plugins/http";
import { readJwtTmr } from "../utils/jwtSubject";

/** 创始人租户下拉数据；非创始人不请求列表。 */
export function useAdminFounderTenantOptions() {
  const isFounder = computed(
    () => readJwtTmr(localStorage.getItem(AI_ADMIN_ACCESS_TOKEN_KEY)) === "FOUNDER",
  );
  const tenantOptions = ref<TenantRow[]>([]);

  async function loadTenants() {
    if (!isFounder.value) {
      return;
    }
    try {
      tenantOptions.value = await listTenants();
    } catch {
      tenantOptions.value = [];
    }
  }

  onMounted(() => {
    void loadTenants();
  });

  return { isFounder, tenantOptions, loadTenants };
}

/**
 * 访问日志 / 审计 / 计量 / 对话日志等「创始人可全量、可收窄」列表的租户筛选。
 * 清空下拉表示不按租户过滤（全量）。
 */
export function useAdminFounderListTenantFilter() {
  const { isFounder, tenantOptions, loadTenants } = useAdminFounderTenantOptions();
  const listFilterTenantId = ref<number | undefined>(undefined);

  function listFilterQuery(): { filterTenantId?: number } {
    if (!isFounder.value || listFilterTenantId.value == null) {
      return {};
    }
    return { filterTenantId: listFilterTenantId.value };
  }

  return { isFounder, tenantOptions, listFilterTenantId, listFilterQuery, loadTenants };
}
