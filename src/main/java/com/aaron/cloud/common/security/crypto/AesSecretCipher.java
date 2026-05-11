package com.aaron.cloud.common.security.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 用于 LLM API Key 等敏感字段的落库加解密（AES-256-GCM）。 */
@Component
public class AesSecretCipher {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final byte[] keyBytes;

    public AesSecretCipher(
            @Value("${ai.llm-model.api-key-encryption-secret:}") String explicitSecret,
            @Value("${ai.auth.jwt-local.secret:}") String jwtFallback) {
        String raw = explicitSecret != null && !explicitSecret.isBlank() ? explicitSecret : jwtFallback;
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "须配置至少 32 字节的 ai.llm-model.api-key-encryption-secret（或保证 ai.auth.jwt-local.secret >= 32 字节）以加密存储模型 API Key");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            this.keyBytes = Arrays.copyOf(sha.digest(raw.getBytes(StandardCharsets.UTF_8)), 32);
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密组件失败", e);
        }
    }

    public String encryptToBase64(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
        buf.put(iv);
        buf.put(cipherText);
        return Base64.getEncoder().encodeToString(buf.array());
    }

    public String decryptFromBase64(String cipherBase64) throws Exception {
        if (cipherBase64 == null || cipherBase64.isBlank()) {
            return null;
        }
        byte[] all = Base64.getDecoder().decode(cipherBase64.trim());
        if (all.length < GCM_IV_LENGTH + 2) {
            throw new IllegalArgumentException("密文格式无效");
        }
        ByteBuffer buf = ByteBuffer.wrap(all);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buf.get(iv);
        byte[] cipherBytes = new byte[buf.remaining()];
        buf.get(cipherBytes);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
