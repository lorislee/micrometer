/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.statistics.StatisticsGateway;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class EhCache2Metrics implements MeterBinder {

    private final String name;

    private final Iterable<Tag> tags;

    private final StatisticsGateway stats;

    public EhCache2Metrics(Ehcache cache, String name, Iterable<Tag> tags) {
        this.stats = cache.getStatistics();
        this.name = name;
        this.tags = Tags.concat(tags, "name", cache.getName());
    }

    /**
     * Record metrics on a JCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, String name, String... tags) {
        return monitor(registry, cache, name, Tags.zip(tags));
    }

    /**
     * Record metrics on a JCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, String name, Iterable<Tag> tags) {
        new EhCache2Metrics(cache, name, tags).bindTo(registry);
        return cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(this.name + ".size", this.stats, StatisticsGateway::getSize)
            .tags(this.tags).tags("where", "local")
            .description("The number of entries held locally in this cache")
            .register(registry);

        Gauge.builder(this.name + ".size", this.stats, StatisticsGateway::getRemoteSize)
            .tags(this.tags).tags("where", "remote")
            .description("The number of entries held remotely in this cache")
            .register(registry);

        FunctionCounter.builder(this.name + ".evictions", this.stats, StatisticsGateway::cacheEvictedCount)
            .tags(this.tags)
            .description("Cache evictions")
            .register(registry);

        FunctionCounter.builder(this.name + ".removals", this.stats, StatisticsGateway::cacheRemoveCount)
            .tags(this.tags)
            .description("Cache removals")
            .register(registry);

        FunctionCounter.builder(this.name + ".puts", this.stats, StatisticsGateway::cachePutAddedCount)
            .tags(this.tags).tags("result", "added")
            .description("Cache puts resulting in a new key/value pair")
            .register(registry);

        FunctionCounter.builder(this.name + ".puts", this.stats, StatisticsGateway::cachePutAddedCount)
            .tags(this.tags).tags("result", "updated")
            .description("Cache puts resulting in an updated value")
            .register(registry);

        requestMetrics(registry);
        commitTransactionMetrics(registry);
        rollbackTransactionMetrics(registry);
        recoveryTransactionMetrics(registry);

        Gauge.builder(this.name + ".local.offheap.size", this.stats, StatisticsGateway::getLocalOffHeapSize)
            .tags(this.tags)
            .description("Local off-heap size")
            .baseUnit("bytes")
            .register(registry);

        Gauge.builder(this.name + ".local.heap.size", this.stats, StatisticsGateway::getLocalHeapSizeInBytes)
            .tags(this.tags)
            .description("Local heap size")
            .baseUnit("bytes")
            .register(registry);

        Gauge.builder(this.name + ".local.disk.size", this.stats, StatisticsGateway::getLocalDiskSizeInBytes)
            .tags(this.tags)
            .description("Local disk size")
            .baseUnit("bytes")
            .register(registry);
    }

    private void requestMetrics(MeterRegistry registry) {
        FunctionCounter.builder(this.name + ".requests", this.stats, StatisticsGateway::cacheMissExpiredCount)
            .tags(this.tags).tags("result", "miss", "reason", "expired")
            .description("The number of times cache lookup methods have not returned a value, due to expiry")
            .register(registry);

        FunctionCounter.builder(this.name + ".requests", this.stats, StatisticsGateway::cacheMissNotFoundCount)
            .tags(this.tags).tags("result", "miss", "reason", "notFound")
            .description("The number of times cache lookup methods have not returned a value, because the key was not found")
            .register(registry);

        FunctionCounter.builder(this.name + ".requests", this.stats, StatisticsGateway::cacheHitCount)
            .tags(this.tags).tags("result", "hit")
            .description("The number of times cache lookup methods have returned a cached value.")
            .register(registry);
    }

    private void commitTransactionMetrics(MeterRegistry registry) {
        FunctionCounter.builder(this.name + ".xa.commits", this.stats, StatisticsGateway::xaCommitReadOnlyCount)
            .tags(this.tags).tags("result", "readOnly")
            .description("Transaction commits that had a read-only result")
            .register(registry);

        FunctionCounter.builder(this.name + ".xa.commits", this.stats, StatisticsGateway::xaCommitExceptionCount)
            .tags(this.tags).tags("result", "exception")
            .description("Transaction commits that failed")
            .register(registry);

        FunctionCounter.builder(this.name + ".xa.commits", this.stats, StatisticsGateway::xaCommitCommittedCount)
            .tags(this.tags).tags("result", "committed")
            .description("Transaction commits that failed")
            .register(registry);
    }

    private void rollbackTransactionMetrics(MeterRegistry registry) {
        FunctionCounter.builder(this.name + ".xa.rollbacks", this.stats, StatisticsGateway::xaRollbackExceptionCount)
            .tags(this.tags).tags("result", "exception")
            .description("Transaction rollbacks that failed")
            .register(registry);

        FunctionCounter.builder(this.name + ".xa.rollbacks", this.stats, StatisticsGateway::xaRollbackSuccessCount)
            .tags(this.tags).tags("result", "success")
            .description("Transaction rollbacks that failed")
            .register(registry);
    }

    private void recoveryTransactionMetrics(MeterRegistry registry) {
        FunctionCounter.builder(this.name + ".xa.recoveries", this.stats, StatisticsGateway::xaRecoveryNothingCount)
            .tags(this.tags).tags("result", "nothing")
            .description("Recovery transactions that recovered nothing")
            .register(registry);

        FunctionCounter.builder(this.name + ".xa.recoveries", this.stats, StatisticsGateway::xaRecoveryRecoveredCount)
            .tags(this.tags).tags("result", "success")
            .description("Successful recovery transaction")
            .register(registry);
    }

}
