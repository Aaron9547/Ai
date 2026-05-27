<template>
  <el-autocomplete
    v-model="model"
    class="auth-email-input"
    :fetch-suggestions="fetchSuggestions"
    :trigger-on-focus="false"
    :debounce="0"
    :placeholder="placeholder"
    :maxlength="128"
    clearable
    fit-input-width
    :teleported="false"
    popper-class="auth-email-suffix-popper"
    :input-props="inputProps"
    @select="onSelect"
  />
</template>

<script setup lang="ts">
import type { AutocompleteFetchSuggestionsCallback } from "element-plus";
import { computed } from "vue";
import { buildEmailSuffixSuggestions } from "../../utils/emailSuffixFill";

const model = defineModel<string>({ required: true });

const props = withDefaults(
  defineProps<{
    placeholder?: string;
    autocomplete?: string;
  }>(),
  { placeholder: "", autocomplete: "email" },
);

const inputProps = computed(() => ({
  type: "email" as const,
  autocomplete: props.autocomplete,
  maxlength: 128,
}));

const fetchSuggestions = (
  queryString: string,
  cb: AutocompleteFetchSuggestionsCallback,
): void => {
  cb(buildEmailSuffixSuggestions(queryString));
};

function onSelect(item: Record<string, string>): void {
  if (item.value) {
    model.value = item.value;
  }
}
</script>

<style scoped>
.auth-email-input {
  width: 100%;
}

.auth-email-input :deep(.el-input) {
  width: 100%;
}
</style>

<style>
.auth-email-suffix-popper.el-popper {
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
}

.auth-email-suffix-popper .el-autocomplete-suggestion li {
  font-size: 13px;
  line-height: 1.4;
  padding: 8px 12px;
}
</style>
