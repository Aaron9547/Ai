package com.aaron.cloud.rag.ltr;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalHitSource;
import com.aaron.cloud.rag.RagRetrievalScoredHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** LTR 特征抽取 + 打分排序（builtin 线性权重；训练模型 JSON 可覆盖）。 */
public final class RagLtrFeatureExtractor {

    public static final int FEATURE_COUNT = 8;

    private RagLtrFeatureExtractor() {}

    public static double[] extractFeatures(
            String query,
            RagRetrievalScoredHit hit,
            int milvusRank,
            int esRank,
            double maxKeywordScore) {
        double[] f = new double[FEATURE_COUNT];
        f[0] = hit.vectorSimilarity() == null ? 0.0d : hit.vectorSimilarity();
        f[1] = normalizeKeyword(hit.keywordScore(), maxKeywordScore);
        f[2] = reciprocalRank(milvusRank);
        f[3] = reciprocalRank(esRank);
        f[4] = tokenOverlap(query, snippetText(hit));
        f[5] = tokenOverlap(query, titleText(hit));
        f[6] = hit.source() == RagRetrievalHitSource.MILVUS ? 1.0d : 0.0d;
        f[7] = queryLengthNorm(query);
        return f;
    }

    public static List<RagRetrievalScoredHit> rank(
            String query,
            List<RagRetrievalScoredHit> candidates,
            double[] weights) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        double maxKw = 0.0d;
        for (RagRetrievalScoredHit h : candidates) {
            if (h.keywordScore() != null && h.keywordScore() > maxKw) {
                maxKw = h.keywordScore();
            }
        }
        int milvusRank = 0;
        int esRank = 0;
        record Scored(RagRetrievalScoredHit hit, double score) {}
        var scored = new ArrayList<Scored>();
        for (RagRetrievalScoredHit h : candidates) {
            int mRank = 0;
            int eRank = 0;
            if (h.source() == RagRetrievalHitSource.MILVUS) {
                milvusRank++;
                mRank = milvusRank;
            } else {
                esRank++;
                eRank = esRank;
            }
            double[] features = extractFeatures(query, h, mRank, eRank, maxKw);
            double s = dot(features, weights);
            scored.add(new Scored(h, s));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        var out = new ArrayList<RagRetrievalScoredHit>();
        for (Scored s : scored) {
            out.add(s.hit());
        }
        return out;
    }

    /** builtin-v1 手工权重（向量相似度优先，BM25 与 rank 特征辅助）。 */
    public static double[] builtinWeights() {
        return new double[] {0.42d, 0.22d, 0.12d, 0.08d, 0.08d, 0.04d, 0.02d, 0.02d};
    }

    private static double dot(double[] a, double[] w) {
        double s = 0.0d;
        int n = Math.min(a.length, w.length);
        for (int i = 0; i < n; i++) {
            s += a[i] * w[i];
        }
        return s;
    }

    private static double normalizeKeyword(Double score, double max) {
        if (score == null || score <= 0.0d || max <= 0.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, score / max);
    }

    private static double reciprocalRank(int rank) {
        if (rank <= 0) {
            return 0.0d;
        }
        return 1.0d / (rank + 10.0d);
    }

    private static double queryLengthNorm(String query) {
        int len = query == null ? 0 : query.trim().length();
        return Math.min(1.0d, len / 64.0d);
    }

    private static double tokenOverlap(String query, String text) {
        if (query == null || text == null || query.isBlank() || text.isBlank()) {
            return 0.0d;
        }
        var qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return 0.0d;
        }
        var tTokens = tokenize(text);
        if (tTokens.isEmpty()) {
            return 0.0d;
        }
        int hit = 0;
        for (String q : qTokens) {
            if (tTokens.contains(q)) {
                hit++;
            }
        }
        return (double) hit / qTokens.size();
    }

    private static java.util.Set<String> tokenize(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).split("[\\s,，;；|｜/、。！？!?]+");
        var set = new java.util.LinkedHashSet<String>();
        for (String p : parts) {
            String t = p.trim();
            if (t.length() >= 2) {
                set.add(t);
            }
        }
        return set;
    }

    private static String snippetText(RagRetrievalScoredHit hit) {
        var c = hit.citation();
        return c.contentPreview() == null ? "" : c.contentPreview();
    }

    private static String titleText(RagRetrievalScoredHit hit) {
        var c = hit.citation();
        return c.documentTitle() == null ? "" : c.documentTitle();
    }
}
