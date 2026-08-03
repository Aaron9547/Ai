package com.aaron.cloud.chat.intent.followup;

import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 多轮意图追问文案单一真源：处理器在回合结束处仅传入 {@link IntentFollowUpContext}，不在各 Runner 内写死 chip。
 */
@UtilityClass
public final class IntentFollowUpPromptCatalog {

    private static final Logger log = LoggerFactory.getLogger(IntentFollowUpPromptCatalog.class);

    private static final int MAX_CHIPS = 3;

    public static List<String> resolve(IntentFollowUpContext ctx) {
        if (ctx == null || ctx.stage() == null || ctx.stage() == IntentFollowUpStage.NONE) {
            return List.of();
        }
        ChatIntentHandlerKind kind = ctx.handlerKind();
        if (kind == null) {
            return List.of();
        }
        List<String> raw =
                switch (kind) {
                    case TRAVEL_REIMBURSEMENT -> travel(ctx);
                    case ONE_SENTENCE_REMINDER -> reminder(ctx);
                    default -> List.of();
                };
        List<String> limited = IntentKeywordPhraseRules.limitForUi(raw, MAX_CHIPS);
        if (!limited.isEmpty()) {
            log.info(
                    "[意图·追问] handler={} stage={} prompts={}",
                    kind,
                    ctx.stage(),
                    limited);
        }
        return limited;
    }

    private static List<String> travel(IntentFollowUpContext ctx) {
        return switch (ctx.stage()) {
            case TRAVEL_AFTER_DOC_COMPLETE ->
                    IntentKeywordPhraseRules.planContinuePhrases(
                            ctx.keywords(), ctx.flowRound(), List.of("继续", "下一步", "安排行程"));
            default -> List.of();
        };
    }

    private static List<String> reminder(IntentFollowUpContext ctx) {
        return switch (ctx.stage()) {
            case REMINDER_AFTER_SUCCESS -> IntentKeywordPhraseRules.cancelPhrases(ctx.keywords());
            default -> List.of();
        };
    }
}
