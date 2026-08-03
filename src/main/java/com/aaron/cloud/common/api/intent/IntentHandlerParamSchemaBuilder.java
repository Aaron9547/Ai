package com.aaron.cloud.common.api.intent;

import com.aaron.cloud.common.api.dto.IntentHandlerConfigFieldMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将 {@link IntentHandlerParamSpec} 枚举常量转为管理端 schema DTO。
 */
public final class IntentHandlerParamSchemaBuilder {

    private IntentHandlerParamSchemaBuilder() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<IntentHandlerConfigFieldMeta> build(Class<? extends Enum> enumClass) {
        if (enumClass == null) {
            return List.of();
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return List.of();
        }
        List<IntentHandlerConfigFieldMeta> out = new ArrayList<>();
        for (Object c : constants) {
            if (!(c instanceof IntentHandlerParamSpec spec)) {
                continue;
            }
            out.add(
                    new IntentHandlerConfigFieldMeta(
                            spec.paramName(),
                            spec.labelZh(),
                            spec.valueKind(),
                            spec.required(),
                            spec.sortOrder(),
                            spec.placeholder().isEmpty() ? null : spec.placeholder(),
                            spec.paramStorage(),
                            spec.intMin(),
                            spec.intMax(),
                            spec.selectOptions()));
        }
        out.sort(Comparator.comparingInt(IntentHandlerConfigFieldMeta::sortOrder));
        return List.copyOf(out);
    }
}
