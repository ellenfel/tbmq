/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(prefix = "cache", value = "type", havingValue = "caffeine", matchIfMissing = true)
@EnableCaching
@Data
@Slf4j
public class TbCaffeineCacheConfiguration {

    private final CacheSpecsMap configuration;

    @Value("${cache.stats.enabled:true}")
    private boolean cacheStatsEnabled;
    @Value("${cache.stats.intervalSec:60}")
    private long cacheStatsInterval;
    @Value("${mqtt.sessions-limit:0}")
    private int sessionsLimit;
    @Value("${mqtt.application-clients-limit:0}")
    @Setter
    private int applicationClientsLimit;

    private ScheduledExecutorService scheduler = null;

    List<CaffeineCache> caches = Collections.emptyList();

    @Bean
    public CacheManager cacheManager() {
        if (log.isTraceEnabled()) {
            log.trace("Initializing cache: {} specs {}", Arrays.toString(RemovalCause.values()), configuration.getCacheSpecs());
        }
        SimpleCacheManager manager = new SimpleCacheManager();
        if (configuration.getCacheSpecs() != null) {
            caches =
                    configuration.getCacheSpecs().entrySet().stream()
                            .map(entry -> buildCache(entry.getKey(),
                                    entry.getValue()))
                            .collect(Collectors.toList());
            if (sessionsLimit > 0) {
                caches.add(buildCache(CacheConstants.CLIENT_SESSIONS_LIMIT_CACHE, getCacheSpecsForLimitCache()));
            }
            if (applicationClientsLimit > 0) {
                caches.add(buildCache(CacheConstants.APP_CLIENTS_LIMIT_CACHE, getCacheSpecsForLimitCache()));
            }
            manager.setCaches(caches);
        }

        //SimpleCacheManager is not a bean (will be wrapped), so call initializeCaches manually
        manager.initializeCaches();

        return new TransactionAwareCacheManagerProxy(manager);
    }

    @PostConstruct
    public void init() {
        if (cacheStatsEnabled) {
            if (log.isDebugEnabled()) {
                log.debug("initializing cache stats scheduled job");
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("cache-stats"));
            scheduler.scheduleAtFixedRate(this::printCacheStats, cacheStatsInterval, cacheStatsInterval, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    void printCacheStats() {
        caches.forEach((cache) -> {
            CacheStats stats = cache.getNativeCache().stats();
            if (stats.hitCount() != 0 || stats.missCount() != 0) {
                log.info("Caffeine [{}] hit [{}] [{}]", cache.getName(), stats.hitRate(), stats);
            }
        });
    }

    private CaffeineCache buildCache(String name, CacheSpecs cacheSpec) {
        final Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
                .weigher(collectionSafeWeigher())
                .maximumWeight(cacheSpec.getMaxSize())
                .ticker(ticker());
        if (cacheSpec.getTimeToLiveInMinutes() != -1) {
            caffeineBuilder.expireAfterWrite(cacheSpec.getTimeToLiveInMinutes(), TimeUnit.MINUTES);
        }
        if (cacheStatsEnabled) {
            caffeineBuilder.recordStats();
        }
        return new CaffeineCache(name, caffeineBuilder.build());
    }

    @Bean
    public Ticker ticker() {
        return Ticker.systemTicker();
    }

    private Weigher<? super Object, ? super Object> collectionSafeWeigher() {
        return (Weigher<Object, Object>) (key, value) -> {
            if (value instanceof Collection) {
                return ((Collection) value).size();
            }
            return 1;
        };
    }

    private CacheSpecs getCacheSpecsForLimitCache() {
        CacheSpecs cacheSpec = new CacheSpecs();
        cacheSpec.setMaxSize(1);
        cacheSpec.setTimeToLiveInMinutes(-1);
        return cacheSpec;
    }

}
