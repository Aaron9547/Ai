package com.aaron.cloud.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 灏嗘煡璇㈡枃鏈槧灏勪负鍥哄畾缁存诞鐐瑰悜閲忥紝渚?Milvus 鍗犱綅妫€绱紱鍚庣画鍙浛鎹负鐪熷疄 embedding 鏈嶅姟鑰屼笉鏀圭紪鎺掑叆鍙ｃ€? */
public final class RagQueryEmbeddingHasher {

    /** 涓庡父瑙佸皬缁村害 embedding 瀵归綈锛屼究浜?Milvus collection schema 鏀舵暃銆?*/
    public static final int DEFAULT_DIM = 128;

    private RagQueryEmbeddingHasher() {}

    public static float[] hashToVector(String query, int dimensions) {
        if (dimensions < 8) {
            dimensions = 8;
        }
        byte[] seed = sha256(query == null ? "" : query);
        float[] v = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            int b1 = seed[i % seed.length] & 0xff;
            int b2 = seed[(i + 3) % seed.length] & 0xff;
            v[i] = ((b1 << 8 | b2) / 65535.0f) * 2f - 1f;
        }
        normalizeL2(v);
        return v;
    }

    private static void normalizeL2(float[] v) {
        double s = 0;
        for (float f : v) {
            s += (double) f * f;
        }
        if (s < 1e-12) {
            Arrays.fill(v, 0f);
            if (v.length > 0) {
                v[0] = 1f;
            }
            return;
        }
        float inv = (float) (1.0 / Math.sqrt(s));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }
}
