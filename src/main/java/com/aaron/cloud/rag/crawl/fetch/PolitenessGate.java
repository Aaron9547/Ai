package com.aaron.cloud.rag.crawl.fetch;

import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** per-host 令牌桶 + 全局并发 + robots.txt（硬编码 respect）+ 日配额。 */
@Slf4j
@Component
public class PolitenessGate {

    private static final Duration ROBOTS_CACHE_TTL = Duration.ofHours(24);
    private static final String ROBOTS_REDIS_PREFIX = "crawl:robots:";

    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final Map<String, HostLimiter> hostLimiters = new ConcurrentHashMap<>();
    /** host → robots.txt 正文；与 {@link #robotsFetchedAtByHost} 配对，避免内部类热更新 ClassNotFound。 */
    private final Map<String, String> robotsBodyByHost = new ConcurrentHashMap<>();
    private final Map<String, Instant> robotsFetchedAtByHost = new ConcurrentHashMap<>();
    private volatile Semaphore globalSemaphore = new Semaphore(16);
    private final ThreadLocal<HostLimiter> activeHostLimiter = new ThreadLocal<>();

    public PolitenessGate(ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void configure(EffectiveSiteCrawlPolicy.PolitenessPolicy policy) {
        globalSemaphore = new Semaphore(Math.max(1, policy.globalConcurrency()));
    }

    /** 阻塞直至允许对该 host 发起一次 HTTP GET；robots 禁止时抛 {@link RobotsDisallowedException}。 */
    public void acquire(String url, EffectiveSiteCrawlPolicy.PolitenessPolicy policy) throws Exception {
        String host = hostOf(url);
        if (policy.respectRobots() && isRobotsDisallowed(host, url)) {
            throw new RobotsDisallowedException(host, url);
        }
        checkDailyQuota(host, policy);
        globalSemaphore.acquire();
        try {
            HostLimiter limiter =
                    hostLimiters.computeIfAbsent(
                            host, h -> new HostLimiter(Math.max(1, policy.perHostConcurrency())));
            limiter.reconfigure(Math.max(1, policy.perHostConcurrency()));
            limiter.acquireSlot(policy);
            activeHostLimiter.set(limiter);
            int jitter =
                    policy.jitterMsMin()
                            + (policy.jitterMsMax() > policy.jitterMsMin()
                                    ? ThreadLocalRandom.current()
                                            .nextInt(policy.jitterMsMax() - policy.jitterMsMin())
                                    : 0);
            if (jitter > 0) {
                Thread.sleep(jitter);
            }
        } catch (Exception e) {
            globalSemaphore.release();
            activeHostLimiter.remove();
            throw e;
        }
    }

    public void release() {
        HostLimiter limiter = activeHostLimiter.get();
        if (limiter != null) {
            limiter.releaseSlot();
            activeHostLimiter.remove();
        }
        globalSemaphore.release();
    }

    public void onRateLimited(String url) {
        String host = hostOf(url);
        hostLimiters.computeIfAbsent(host, h -> new HostLimiter(2)).slowDown();
    }

    private void checkDailyQuota(String host, EffectiveSiteCrawlPolicy.PolitenessPolicy policy) {
        int max = policy.dailyMaxRequestsPerHost();
        if (max <= 0) {
            return;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        String key = "crawl:quota:" + host + ":" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (n != null && n > max) {
            throw new IllegalStateException("daily quota exceeded for host " + host);
        }
    }

    private boolean isRobotsDisallowed(String host, String url) {
        String body = loadRobotsBody(host);
        if (body == null) {
            return false;
        }
        return RobotsRules.disallowsPath(body, url);
    }

    private String loadRobotsBody(String host) {
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis != null) {
            try {
                String cached = redis.opsForValue().get(ROBOTS_REDIS_PREFIX + host);
                if (cached != null) {
                    return cached.isEmpty() ? null : cached;
                }
            } catch (Exception e) {
                log.debug("robots redis read skip host={}", host);
            }
        }
        Instant fetchedAt = robotsFetchedAtByHost.get(host);
        if (fetchedAt != null && fetchedAt.plus(ROBOTS_CACHE_TTL).isAfter(Instant.now())) {
            return robotsBodyByHost.get(host);
        }
        String body = fetchRobotsTxt(host);
        robotsFetchedAtByHost.put(host, Instant.now());
        if (body != null) {
            robotsBodyByHost.put(host, body);
        } else {
            robotsBodyByHost.remove(host);
        }
        if (redis != null && body != null) {
            try {
                redis.opsForValue().set(ROBOTS_REDIS_PREFIX + host, body, ROBOTS_CACHE_TTL);
            } catch (Exception e) {
                log.debug("robots redis write skip host={}", host);
            }
        } else if (redis != null) {
            try {
                redis.opsForValue().set(ROBOTS_REDIS_PREFIX + host, "", ROBOTS_CACHE_TTL);
            } catch (Exception ignored) {
                // skip
            }
        }
        return body;
    }

    private static String fetchRobotsTxt(String host) {
        try {
            String robotsUrl = "https://" + host + "/robots.txt";
            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            HttpRequest req =
                    HttpRequest.newBuilder(URI.create(robotsUrl))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body();
            }
        } catch (Exception e) {
            log.debug("robots fetch skip host={} err={}", host, e.toString());
        }
        return null;
    }

    private static String hostOf(String url) {
        try {
            URI u = URI.create(url);
            return u.getHost() != null ? u.getHost().toLowerCase() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static final class RobotsDisallowedException extends Exception {
        public RobotsDisallowedException(String host, String url) {
            super("robots disallow host=" + host + " url=" + url);
        }
    }

    private static final class RobotsRules {
        static boolean disallowsPath(String body, String url) {
            try {
                String path = URI.create(url).getPath();
                if (path == null || path.isEmpty()) {
                    path = "/";
                }
                boolean active = false;
                for (String line : body.split("\n")) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    String lower = t.toLowerCase(java.util.Locale.ROOT);
                    if (lower.startsWith("user-agent:")) {
                        String ua = t.substring(11).trim();
                        active = "*".equals(ua) || ua.contains("*");
                        continue;
                    }
                    if (!active) {
                        continue;
                    }
                    if (lower.startsWith("disallow:")) {
                        String prefix = t.substring(9).trim();
                        if (!prefix.isEmpty() && path.startsWith(prefix)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        private RobotsRules() {}
    }

    private static final class HostLimiter {
        private volatile Semaphore hostSem;
        private volatile double intervalMs = 2000;
        private final AtomicLong nextAllowedNanos = new AtomicLong(0);

        HostLimiter(int permits) {
            this.hostSem = new Semaphore(Math.max(1, permits));
        }

        void reconfigure(int permits) {
            int p = Math.max(1, permits);
            if (hostSem.availablePermits() != p) {
                hostSem = new Semaphore(p);
            }
        }

        void acquireSlot(EffectiveSiteCrawlPolicy.PolitenessPolicy policy) throws InterruptedException {
            hostSem.acquire();
            double qps = Math.max(0.05, policy.perHostQps());
            intervalMs = 1000.0 / qps;
            long wait;
            while ((wait = nextAllowedNanos.get() - System.nanoTime()) > 0) {
                Thread.sleep(TimeUnit.NANOSECONDS.toMillis(wait) + 1);
            }
            nextAllowedNanos.set(System.nanoTime() + (long) (intervalMs * 1_000_000));
        }

        void releaseSlot() {
            hostSem.release();
        }

        void slowDown() {
            intervalMs = Math.min(intervalMs * 1.5, 30_000);
        }
    }
}
