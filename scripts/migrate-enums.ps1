# One-off: relocate common.api.enums into domain subpackages (P0-P2)
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$root = Join-Path $repoRoot "src\main\java\com\aaron\cloud"
$enumRoot = Join-Path $root "common\api\enums"

$map = @{
    chat      = @(
        'ChatInputBlockReason', 'ChatIntentHandlerKind', 'ChatIntentKeywordKind', 'ChatIntentMatchSource',
        'ChatMessageRole', 'ChatMessageUserFeedback', 'ChatStarterDailyBatchStatus', 'ChatStarterEventType',
        'ChatStarterPromptScene', 'ChatStarterPromptSource', 'ConversationRecordStatus', 'WebSearchCacheTier'
    )
    rag       = @(
        'RagChunkRetrievalEnabled', 'RagChunkStrategy', 'RagDocumentDisplayStatus', 'RagDocumentSourceType',
        'RagRetrievalMode', 'RagWebCrawlSyncMode', 'CrawlQueueRole', 'CrawlQueueStatus', 'SiteCrawlPreset',
        'RagSiteCrawlDiscoveryStrategy'
    )
    llm       = @(
        'LlmAnonymousAccess', 'LlmConnectorKind', 'LlmModelKind', 'LlmModelStatus', 'LlmThinkingCapability',
        'LlmVectorBackend', 'LlmWebSearchProvider'
    )
    tenant    = @('TenantMemberRole', 'TenantRuntimeSettingKey', 'TenantStatus')
    job       = @('JobTaskStatus', 'JobTaskType')
    scheduled = @(
        'ScheduledRunStatus', 'ScheduledRunTrigger', 'ScheduledTaskIntervalPreset', 'TenantScheduledExecutorCode'
    )
    gateway   = @('AdminMenuCode', 'ToggleState')
    profile   = @('ProfileTagCode')
    guardrail = @('GuardrailActionType', 'GuardrailSensitivePoolType')
    identity  = @('UserAccountStatus')
    metering  = @('MeteringMeterType')
    mcp       = @('McpServerStatus')
    eval      = @('EvalRunStatus')
    intent    = @('IntentHandlerConfigValueKind', 'IntentHandlerParamStorage', 'IntentRoute')
    notify    = @('SubscriptionStatus')
    file      = @('FileScanStatus')
    infra     = @(
        'OutboundKind', 'AuthProviderMode', 'FileStorageProviderMode', 'NotificationProviderMode',
        'VectorStoreProviderMode'
    )
}

$sourceOverrides = @{
    WebSearchCacheTier         = Join-Path $root "common\tenant\runtime\WebSearchCacheTier.java"
    OutboundKind               = Join-Path $root "common\outbound\OutboundKind.java"
    AuthProviderMode           = Join-Path $root "common\config\providers\AuthProviderMode.java"
    FileStorageProviderMode    = Join-Path $root "common\config\providers\FileStorageProviderMode.java"
    NotificationProviderMode   = Join-Path $root "common\config\providers\NotificationProviderMode.java"
    VectorStoreProviderMode    = Join-Path $root "common\config\providers\VectorStoreProviderMode.java"
    CrawlQueueRole             = Join-Path $root "rag\crawl\CrawlQueueRole.java"
    CrawlQueueStatus           = Join-Path $root "rag\crawl\CrawlQueueStatus.java"
    SiteCrawlPreset            = Join-Path $root "rag\crawl\policy\SiteCrawlPreset.java"
}

$nameToSub = @{}
foreach ($sub in $map.Keys) {
    foreach ($n in $map[$sub]) { $nameToSub[$n] = $sub }
}

function Rewrite-Package([string]$content, [string]$sub) {
    $newPkg = "com.aaron.cloud.common.api.enums.$sub"
    $content = $content -replace 'package com\.aaron\.cloud\.common\.api\.enums;', "package $newPkg;"
    $content = $content -replace 'package com\.aaron\.cloud\.common\.tenant\.runtime;', "package $newPkg;"
    $content = $content -replace 'package com\.aaron\.cloud\.common\.outbound;', "package $newPkg;"
    $content = $content -replace 'package com\.aaron\.cloud\.common\.config\.providers;', "package $newPkg;"
    $content = $content -replace 'package com\.aaron\.cloud\.rag\.crawl;', "package $newPkg;"
    $content = $content -replace 'package com\.aaron\.cloud\.rag\.crawl\.policy;', "package $newPkg;"
    return $content
}

# RagSiteCrawlDiscoveryStrategy (P1)
$ragDir = Join-Path $enumRoot "rag"
New-Item -ItemType Directory -Force -Path $ragDir | Out-Null
$ragDisc = @'
package com.aaron.cloud.common.api.enums.rag;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 站点爬取 discovery.strategies 白名单（存 JSON 字符串 id）。 */
@Getter
@RequiredArgsConstructor
public enum RagSiteCrawlDiscoveryStrategy {
    SITEMAP("sitemap"),
    HTML_BFS("html_bfs"),
    LIST_PAGINATION("list_pagination"),
    CMS_VSB("cms_vsb"),
    ARTICLE_HEURISTIC("article_heuristic"),
    RSS_ATOM("rss_atom"),
    JS_RENDER_DISCOVERY("js_render_discovery");

    private final String id;

    public static final Set<String> ALL_IDS =
            Arrays.stream(values()).map(RagSiteCrawlDiscoveryStrategy::getId).collect(Collectors.toUnmodifiableSet());

    public static boolean isAllowed(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        return !t.isEmpty() && ALL_IDS.contains(t);
    }

    public static RagSiteCrawlDiscoveryStrategy fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("discovery strategy id required");
        }
        String t = raw.trim();
        return Arrays.stream(values())
                .filter(s -> s.id.equals(t))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown discovery strategy: " + raw));
    }

    public static java.util.Optional<RagSiteCrawlDiscoveryStrategy> tryFromId(String raw) {
        try {
            return java.util.Optional.of(fromId(raw));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
'@
Set-Content -Path (Join-Path $ragDir "RagSiteCrawlDiscoveryStrategy.java") -Value $ragDisc -Encoding UTF8

foreach ($sub in $map.Keys) {
    $dir = Join-Path $enumRoot $sub
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    foreach ($name in $map[$sub]) {
        if ($name -eq 'RagSiteCrawlDiscoveryStrategy') { continue }
        $src = if ($sourceOverrides.ContainsKey($name)) { $sourceOverrides[$name] } else { Join-Path $enumRoot "$name.java" }
        if (-not (Test-Path $src)) {
            Write-Warning "Missing source for ${name}: $src"
            continue
        }
        $content = Get-Content $src -Raw -Encoding UTF8
        $content = Rewrite-Package $content $sub
        Set-Content -Path (Join-Path $dir "$name.java") -Value $content -Encoding UTF8 -NoNewline
    }
}

# Bulk import rewrite in java + test
$importReplacements = @()
foreach ($name in $nameToSub.Keys) {
    $sub = $nameToSub[$name]
    $importReplacements += [ordered]@{
        Old = "import com.aaron.cloud.common.api.enums.$name;"
        New = "import com.aaron.cloud.common.api.enums.$sub.$name;"
    }
}
$importReplacements += @(
    [ordered]@{ Old = 'import com.aaron.cloud.common.tenant.runtime.WebSearchCacheTier;'; New = 'import com.aaron.cloud.common.api.enums.chat.WebSearchCacheTier;' }
    [ordered]@{ Old = 'import com.aaron.cloud.common.outbound.OutboundKind;'; New = 'import com.aaron.cloud.common.api.enums.infra.OutboundKind;' }
    [ordered]@{ Old = 'import com.aaron.cloud.common.config.providers.AuthProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.AuthProviderMode;' }
    [ordered]@{ Old = 'import com.aaron.cloud.common.config.providers.FileStorageProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.FileStorageProviderMode;' }
    [ordered]@{ Old = 'import com.aaron.cloud.common.config.providers.NotificationProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.NotificationProviderMode;' }
    [ordered]@{ Old = 'import com.aaron.cloud.common.config.providers.VectorStoreProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.VectorStoreProviderMode;' }
    [ordered]@{ Old = 'import com.aaron.cloud.rag.crawl.CrawlQueueRole;'; New = 'import com.aaron.cloud.common.api.enums.rag.CrawlQueueRole;' }
    [ordered]@{ Old = 'import com.aaron.cloud.rag.crawl.CrawlQueueStatus;'; New = 'import com.aaron.cloud.common.api.enums.rag.CrawlQueueStatus;' }
    [ordered]@{ Old = 'import com.aaron.cloud.rag.crawl.policy.SiteCrawlPreset;'; New = 'import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;' }
    [ordered]@{ Old = 'import com.aaron.cloud.rag.crawl.policy.SiteCrawlStrategyIds;'; New = 'import com.aaron.cloud.common.api.enums.rag.RagSiteCrawlDiscoveryStrategy;' }
)

$codeRoot = $repoRoot
$javaFiles = Get-ChildItem -Path $codeRoot -Recurse -Include *.java -File |
    Where-Object { $_.FullName -notmatch '\\target\\' }

foreach ($file in $javaFiles) {
    $text = Get-Content $file.FullName -Raw -Encoding UTF8
    $orig = $text
    foreach ($r in $importReplacements) {
        $text = $text.Replace($r.Old, $r.New)
    }
  # SiteCrawlStrategyIds.ALL -> RagSiteCrawlDiscoveryStrategy.ALL_IDS
    $text = $text.Replace('SiteCrawlStrategyIds.ALL', 'RagSiteCrawlDiscoveryStrategy.ALL_IDS')
    if ($text -ne $orig) {
        Set-Content -Path $file.FullName -Value $text -Encoding UTF8 -NoNewline
    }
}

# Delete old flat enum files and relocated sources
foreach ($name in $nameToSub.Keys) {
    if ($sourceOverrides.ContainsKey($name)) { continue }
    $old = Join-Path $enumRoot "$name.java"
    if (Test-Path $old) { Remove-Item $old -Force }
}
foreach ($path in $sourceOverrides.Values) {
    if (Test-Path $path) { Remove-Item $path -Force }
}
$strategyIds = Join-Path $root "rag\crawl\policy\SiteCrawlStrategyIds.java"
if (Test-Path $strategyIds) { Remove-Item $strategyIds -Force }

Write-Host "Enum migration complete. Subpackages under $enumRoot"
