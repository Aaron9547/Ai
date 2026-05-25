package com.aaron.cloud.chat.rest.open;

import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendClickBody;
import com.aaron.cloud.chat.dto.ChatDailyRecommendDtos.DailyRecommendResponse;
import com.aaron.cloud.chat.recommend.ChatUserDailyRecommendService;
import com.aaron.cloud.common.web.rest.OpenV1ControllerBases;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatDailyRecommendController extends OpenV1ControllerBases.Chat {

    private final ChatUserDailyRecommendService dailyRecommendService;

    /** 获取当日个性化资讯推荐；若当日尚未生成则同步触发联网+模型生成（服务端按主体去重）。 */
    @GetMapping("/daily-recommend")
    public DailyRecommendResponse dailyRecommend() {
        return dailyRecommendService.getOrGenerateForCurrentSubject();
    }

    /** 当日首次生成失败后的唯一一次重试。 */
    @PostMapping("/daily-recommend/retry")
    public DailyRecommendResponse retryDailyRecommend() {
        return dailyRecommendService.retryForCurrentSubject();
    }

    /** 登录后强制按用户画像重新生成当日推荐（覆盖访客阶段结果）。 */
    @PostMapping("/daily-recommend/regenerate-on-login")
    public DailyRecommendResponse regenerateOnLogin() {
        return dailyRecommendService.regenerateForLoggedInUser();
    }

    /** 资讯卡片点击埋点；始终 204，画像写入失败不影响主流程。 */
    @PostMapping("/daily-recommend/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clickDailyRecommend(@Valid @RequestBody DailyRecommendClickBody body) {
        dailyRecommendService.recordClick(body);
    }
}
