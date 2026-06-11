<script setup lang="ts">
import { Document, Picture } from "@element-plus/icons-vue";
import { isAttachImageKind, type ShareCaptureUserAttachment } from "@/utils/chatShareAttachments";

defineProps<{
  text?: string;
  attachments?: ShareCaptureUserAttachment[];
}>();
</script>

<template>
  <p v-if="text?.trim()" class="share-user-body__text">{{ text }}</p>
  <ul v-if="attachments?.length" class="share-user-body__attach" role="list">
    <li v-for="a in attachments" :key="a.id" class="share-user-body__attach-item">
      <img
        v-if="a.previewUrl && isAttachImageKind(a.kind)"
        :src="a.previewUrl"
        :alt="a.fileName"
        class="share-user-body__img"
        loading="eager"
      />
      <span v-else class="share-user-body__chip">
        <el-icon :size="14">
          <Picture v-if="isAttachImageKind(a.kind)" />
          <Document v-else />
        </el-icon>
        <span class="share-user-body__chip-name">{{ a.fileName }}</span>
      </span>
    </li>
  </ul>
</template>

<style scoped>
.share-user-body__text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.share-user-body__text + .share-user-body__attach {
  margin-top: 8px;
}

.share-user-body__attach {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.share-user-body__attach-item {
  margin: 0;
  min-width: 0;
}

.share-user-body__img {
  display: block;
  max-width: 100%;
  width: auto;
  height: auto;
  border-radius: 10px;
  border: 1px solid #e4e4e7;
  object-fit: contain;
  background: #fff;
}

.share-user-body__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e4e4e7;
  font-size: 12px;
  color: #3f3f46;
}

.share-user-body__chip-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
