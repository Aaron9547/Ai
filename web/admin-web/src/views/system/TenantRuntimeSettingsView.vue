<template>
  <div class="runtime-settings">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb"
      title="仅本租户（当前管理端工作区 JWT 租户）"
      description="只可查看与修改当前工作区对应租户下的参数；切换工作区即切换目标租户。保存后立即对开放接口等生效，无需重启。启用 Redis 时服务端对缓存键执行「写库前删除、写库后再删除」双删，未启用 Redis 时直接读库。"
    />
    <el-card shadow="never">
      <template #header>
        <span class="hdr">系统参数</span>
      </template>
      <el-table v-loading="loading" :data="rows" border stripe style="width: 100%">
        <el-table-column prop="descriptionZh" label="说明" min-width="220" />
        <el-table-column prop="key" label="键" width="220" />
        <el-table-column label="值" min-width="200">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueKind === 'BOOLEAN'"
              v-model="row.valueText"
              active-value="true"
              inactive-value="false"
            />
            <el-input v-else v-model="row.valueText" clearable maxlength="2048" show-word-limit />
          </template>
        </el-table-column>
      </el-table>
      <div class="actions">
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button :loading="loading" @click="load">重新加载</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import * as api from "@/api/tenantRuntimeSettings";
import { apiRequestErrorMessage } from "@/utils/apiRequestErrorMessage";

const rows = ref<api.TenantRuntimeSettingRow[]>([]);
const loading = ref(false);
const saving = ref(false);

async function load() {
  loading.value = true;
  try {
    rows.value = await api.listTenantRuntimeSettings();
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  try {
    await api.replaceTenantRuntimeSettings(
      rows.value.map((r) => ({ key: r.key, valueText: r.valueText })),
    );
    ElMessage.success("已保存");
    await load();
  } catch (e: unknown) {
    ElMessage.error(apiRequestErrorMessage(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.runtime-settings {
  padding: 8px 4px;
}
.mb {
  margin-bottom: 12px;
}
.hdr {
  font-weight: 600;
}
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
