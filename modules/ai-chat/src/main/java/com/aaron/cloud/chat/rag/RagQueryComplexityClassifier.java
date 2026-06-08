package com.aaron.cloud.chat.rag;

import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 简单问句判定：字数 + 复杂触发词。 */
@Service
public class RagQueryComplexityClassifier {

    private static final Pattern COMPLEX_TRIGGER =
            Pattern.compile(
                    "(对比|比较|分析|为什么|为何|如何|怎么|怎样|步骤|流程|区别|差异|优缺点|并且|以及|同时|分别|详细|全面|深入|总结|归纳|评估|评价|原理|机制|架构|方案|策略|影响|原因|结论|举例|案例|vs|VS|versus|compare|analyze|analysis|why|how|step|steps|difference|versus)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private RagQueryComplexityClassifier() {}

    public com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile classify(
            String query, RagRetrievalTuningRuntime tuning) {
        String q = query == null ? "" : query.trim();
        if (!tuning.simpleQueryFastPathEnabled()) {
            return com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile.COMPLEX_HYBRID;
        }
        if (q.length() > tuning.resolvedSimpleQueryMaxChars()) {
            return com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile.COMPLEX_HYBRID;
        }
        if (COMPLEX_TRIGGER.matcher(q).find()) {
            return com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile.COMPLEX_HYBRID;
        }
        return com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile.SIMPLE_VECTOR;
    }
}
