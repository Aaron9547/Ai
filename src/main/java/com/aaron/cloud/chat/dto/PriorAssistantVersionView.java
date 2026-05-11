package com.aaron.cloud.chat.dto;

/**
 * 鍔╂墜娑堟伅鍦ㄣ€岄噸鏂扮敓鎴愩€嶅墠淇濈暀鐨勪竴鐗堝揩鐓э紙瀛樹簬褰撳墠鍔╂墜琛?meta 鐨?{@code priorVersions} 鏁扮粍椤癸級銆? */
public record PriorAssistantVersionView(
        String content,
        String reasoning,
        String modelAlias,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {}
