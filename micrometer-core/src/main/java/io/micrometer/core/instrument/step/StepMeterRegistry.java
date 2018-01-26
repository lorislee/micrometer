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

package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Registry that step-normalizes counts and sums to a rate/second over the publishing interval.
 *
 * @author Jon Schneider
 */
public abstract class StepMeterRegistry extends MeterRegistry {
    private final StepRegistryConfig config;
    @Nullable
    private ScheduledFuture<?> publisher;

    public StepMeterRegistry(StepRegistryConfig config, Clock clock) {
        super(clock);
        this.config = config;
    }

    public void start() {
        start(Executors.defaultThreadFactory());
    }

    public void start(ThreadFactory threadFactory) {
        if (publisher != null)
            stop();

        publisher = Executors.newSingleThreadScheduledExecutor(threadFactory)
            .scheduleAtFixedRate(this::publish, config.step().toMillis(), config.step().toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (publisher != null) {
            publisher.cancel(false);
            publisher = null;
        }
    }

    protected abstract void publish();

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> f) {
        return new DefaultGauge<>(id, obj, f);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StepCounter(id, clock, config.step().toMillis());
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected Timer newTimer(Meter.Id id, HistogramConfig histogramConfig, PauseDetector pauseDetector) {
        return new StepTimer(id, clock, histogramConfig, pauseDetector, getBaseTimeUnit());
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        return new StepDistributionSummary(id, clock, histogramConfig);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return new StepFunctionTimer<>(id, clock, config.step().toMillis(), obj, countFunction, totalTimeFunction, totalTimeFunctionUnits, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return new StepFunctionCounter<>(id, clock, config.step().toMillis(), obj, f);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected HistogramConfig defaultHistogramConfig() {
        return HistogramConfig.builder()
            .histogramExpiry(config.step())
            .build()
            .merge(HistogramConfig.DEFAULT);
    }
}
