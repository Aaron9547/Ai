import { ref } from "vue";

/** 侧栏知识星球入口卡片同步闪烁（如用户发送新消息时 bump） */
const pulseGeneration = ref(0);

export function useKnowledgePlanetPulse() {
  return { pulseGeneration };
}

export function bumpKnowledgePlanetPulse(): void {
  pulseGeneration.value += 1;
}
