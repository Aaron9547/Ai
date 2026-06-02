<template>
  <el-dialog
    v-model="open"
    :title="t('views.profiles.interestNewsDlgTitle')"
    width="640px"
    destroy-on-close
  >
    <el-table v-if="entries.length" :data="entries" border size="small" max-height="420">
      <el-table-column prop="tag" :label="t('views.profiles.interestNewsTableTag')" width="100" show-overflow-tooltip />
      <el-table-column prop="title" :label="t('views.profiles.interestNewsTableTitle')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('views.profiles.interestNewsTableUrl')" width="88" align="center">
        <template #default="{ row }">
          <el-link
            v-if="row.url"
            :href="row.url"
            type="primary"
            target="_blank"
            rel="noopener noreferrer"
            >{{ t("views.profiles.interestNewsOpenLink") }}</el-link
          >
          <span v-else>{{ t("common.dash") }}</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else :description="t('common.dash')" :image-size="64" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { parseInterestNewsJson, type InterestNewsEntry } from "@/utils/interestNewsJson";

const open = defineModel<boolean>("open", { default: false });

const props = defineProps<{
  rawJson: string | null;
}>();

const { t } = useI18n();

const entries = computed((): InterestNewsEntry[] => parseInterestNewsJson(props.rawJson));
</script>
