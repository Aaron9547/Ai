# Finish enum migration: delete flat duplicates + rewrite imports
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$enumRoot = Join-Path $repoRoot "src\main\java\com\aaron\cloud\common\api\enums"

$map = @{
    chat      = @('ChatInputBlockReason','ChatIntentHandlerKind','ChatIntentKeywordKind','ChatIntentMatchSource','ChatMessageRole','ChatMessageUserFeedback','ChatStarterDailyBatchStatus','ChatStarterEventType','ChatStarterPromptScene','ChatStarterPromptSource','ConversationRecordStatus','WebSearchCacheTier')
    rag       = @('RagChunkRetrievalEnabled','RagChunkStrategy','RagDocumentDisplayStatus','RagDocumentSourceType','RagRetrievalMode','RagWebCrawlSyncMode','CrawlQueueRole','CrawlQueueStatus','SiteCrawlPreset','RagSiteCrawlDiscoveryStrategy')
    llm       = @('LlmAnonymousAccess','LlmConnectorKind','LlmModelKind','LlmModelStatus','LlmThinkingCapability','LlmVectorBackend','LlmWebSearchProvider')
    tenant    = @('TenantMemberRole','TenantRuntimeSettingKey','TenantStatus')
    job       = @('JobTaskStatus','JobTaskType')
    scheduled = @('ScheduledRunStatus','ScheduledRunTrigger','ScheduledTaskIntervalPreset','TenantScheduledExecutorCode')
    gateway   = @('AdminMenuCode','ToggleState')
    profile   = @('ProfileTagCode')
    guardrail = @('GuardrailActionType','GuardrailSensitivePoolType')
    identity  = @('UserAccountStatus')
    metering  = @('MeteringMeterType')
    mcp       = @('McpServerStatus')
    eval      = @('EvalRunStatus')
    intent    = @('IntentHandlerConfigValueKind','IntentHandlerParamStorage','IntentRoute')
    notify    = @('SubscriptionStatus')
    file      = @('FileScanStatus')
    infra     = @('OutboundKind','AuthProviderMode','FileStorageProviderMode','NotificationProviderMode','VectorStoreProviderMode')
}

$nameToSub = @{}
foreach ($sub in $map.Keys) {
    foreach ($n in $map[$sub]) { $nameToSub[$n] = $sub }
}

# Delete flat duplicates
Get-ChildItem -Path $enumRoot -Filter *.java -File | ForEach-Object { Remove-Item $_.FullName -Force }

$importReplacements = @()
foreach ($name in ($nameToSub.Keys | Sort-Object)) {
    $sub = $nameToSub[$name]
    $importReplacements += [pscustomobject]@{
        Old = "import com.aaron.cloud.common.api.enums.$name;"
        New = "import com.aaron.cloud.common.api.enums.$sub.$name;"
    }
}
$importReplacements += @(
    [pscustomobject]@{ Old = 'import com.aaron.cloud.common.tenant.runtime.WebSearchCacheTier;'; New = 'import com.aaron.cloud.common.api.enums.chat.WebSearchCacheTier;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.common.outbound.OutboundKind;'; New = 'import com.aaron.cloud.common.api.enums.infra.OutboundKind;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.common.config.providers.AuthProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.AuthProviderMode;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.common.config.providers.FileStorageProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.FileStorageProviderMode;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.common.config.providers.NotificationProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.NotificationProviderMode;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.common.config.providers.VectorStoreProviderMode;'; New = 'import com.aaron.cloud.common.api.enums.infra.VectorStoreProviderMode;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.rag.crawl.CrawlQueueRole;'; New = 'import com.aaron.cloud.common.api.enums.rag.CrawlQueueRole;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.rag.crawl.CrawlQueueStatus;'; New = 'import com.aaron.cloud.common.api.enums.rag.CrawlQueueStatus;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.rag.crawl.policy.SiteCrawlPreset;'; New = 'import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;' }
    [pscustomobject]@{ Old = 'import com.aaron.cloud.rag.crawl.policy.SiteCrawlStrategyIds;'; New = 'import com.aaron.cloud.common.api.enums.rag.RagSiteCrawlDiscoveryStrategy;' }
)

$javaFiles = Get-ChildItem -Path $repoRoot -Recurse -Include *.java -File |
    Where-Object { $_.FullName -notmatch '\\target\\' }

foreach ($file in $javaFiles) {
    $text = [System.IO.File]::ReadAllText($file.FullName)
    if ([string]::IsNullOrEmpty($text)) { continue }
    $orig = $text
    foreach ($r in $importReplacements) {
        $text = $text.Replace($r.Old, $r.New)
    }
    $text = $text.Replace('SiteCrawlStrategyIds.ALL', 'RagSiteCrawlDiscoveryStrategy.ALL_IDS')
    if ($text -ne $orig) {
        [System.IO.File]::WriteAllText($file.FullName, $text)
    }
}

Write-Host "Finished import rewrite and removed flat enum duplicates."
