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

package io.micrometer.core.instrument.histogram;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.CountAtValue;
import io.micrometer.core.instrument.HistogramSnapshot;
import io.micrometer.core.instrument.ValueAtPercentile;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @param <T> the type of the buckets in a ring buffer
 * @param <U> the type of accumulated histogram
 * @author Jon Schneider
 * @author Trustin Heuiseung Lee
 */
@SuppressWarnings("ConstantConditions")
abstract class TimeWindowHistogramBase<T, U> {

    static final int NUM_SIGNIFICANT_VALUE_DIGITS = 2;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<TimeWindowHistogramBase> rotatingUpdater =
        AtomicIntegerFieldUpdater.newUpdater(TimeWindowHistogramBase.class, "rotating");

    private final Clock clock;

    private final HistogramConfig histogramConfig;

    private final T[] ringBuffer;

    private final long durationBetweenRotatesMillis;

    @Nullable
    private U accumulatedHistogram;

    private volatile boolean accumulatedHistogramStale;

    private int currentBucket;

    private volatile long lastRotateTimestampMillis;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    TimeWindowHistogramBase(Clock clock, HistogramConfig histogramConfig, Class<T> bucketType) {
        this.clock = clock;
        this.histogramConfig = validateHistogramConfig(histogramConfig);
        final int ageBuckets = histogramConfig.getHistogramBufferLength();
        if (ageBuckets <= 0) {
            rejectHistogramConfig("histogramBufferLength (" + ageBuckets + ") must be greater than 0.");
        }
        //noinspection unchecked
        this.ringBuffer = (T[]) Array.newInstance(bucketType, ageBuckets);
        this.durationBetweenRotatesMillis = histogramConfig.getHistogramExpiry().toMillis() / ageBuckets;
        if (this.durationBetweenRotatesMillis <= 0) {
            rejectHistogramConfig("histogramExpiry (" + histogramConfig.getHistogramExpiry().toMillis() +
                "ms) / histogramBufferLength (" + ageBuckets + ") must be greater than 0.");
        }
        this.currentBucket = 0;
        this.lastRotateTimestampMillis = clock.wallTime();
    }

    private static HistogramConfig validateHistogramConfig(HistogramConfig histogramConfig) {
        // Validate other HistogramConfig properties we will use later in this class.
        for (double p : histogramConfig.getPercentiles()) {
            if (p < 0 || p > 1) {
                rejectHistogramConfig("percentiles must contain only the values between 0.0 and 1.0. " +
                    "Found " + p);
            }
        }
        final long minimumExpectedValue = histogramConfig.getMinimumExpectedValue();
        final long maximumExpectedValue = histogramConfig.getMaximumExpectedValue();
        if (minimumExpectedValue <= 0) {
            rejectHistogramConfig("minimumExpectedValue (" + minimumExpectedValue + ") must be greater than 0.");
        }
        if (maximumExpectedValue < minimumExpectedValue) {
            rejectHistogramConfig("maximumExpectedValue (" + maximumExpectedValue +
                ") must be equal to or greater than minimumExpectedValue (" +
                minimumExpectedValue + ").");
        }
        for (long sla : histogramConfig.getSlaBoundaries()) {
            if (sla <= 0) {
                rejectHistogramConfig("slaBoundaries must contain only the values greater than 0. " +
                    "Found " + sla);
            }
        }
        return histogramConfig;
    }

    private static void rejectHistogramConfig(String msg) {
        throw new IllegalStateException("Invalid HistogramConfig: " + msg);
    }

    void initRingBuffer() {
        for (int i = 0; i < this.ringBuffer.length; i++) {
            this.ringBuffer[i] = newBucket(this.histogramConfig);
        }
        this.accumulatedHistogram = newAccumulatedHistogram(this.ringBuffer);
    }

    abstract T newBucket(HistogramConfig histogramConfig);

    abstract void recordLong(T bucket, long value);

    abstract void recordDouble(T bucket, double value);

    abstract void resetBucket(T bucket);

    abstract U newAccumulatedHistogram(T[] ringBuffer);

    abstract void accumulate(T sourceBucket, U accumulatedHistogram);

    abstract void resetAccumulatedHistogram(U accumulatedHistogram);

    abstract double valueAtPercentile(U accumulatedHistogram, double percentile);

    abstract double countAtValue(U accumulatedHistogram, long value);

    public final double percentile(double percentile) {
        rotate();
        synchronized (this) {
            accumulateIfStale();
            return valueAtPercentile(this.accumulatedHistogram, percentile * 100);
        }
    }

    public final double percentile(double percentile, TimeUnit unit) {
        return TimeUtils.nanosToUnit(percentile(percentile), unit);
    }

    public final double histogramCountAtValue(long value) {
        rotate();
        synchronized (this) {
            accumulateIfStale();
            return countAtValue(this.accumulatedHistogram, value);
        }
    }

    public final HistogramSnapshot takeSnapshot(long count, double total, double max,
                                                boolean supportsAggregablePercentiles) {
        rotate();
        final ValueAtPercentile[] values;
        final CountAtValue[] counts;
        synchronized (this) {
            accumulateIfStale();
            values = takeValueSnapshot();
            counts = takeCountSnapshot(supportsAggregablePercentiles);
        }
        return HistogramSnapshot.of(count, total, max, values, counts);
    }

    private void accumulateIfStale() {
        if (this.accumulatedHistogramStale) {
            accumulate(this.ringBuffer[this.currentBucket], this.accumulatedHistogram);
            this.accumulatedHistogramStale = false;
        }
    }

    private ValueAtPercentile[] takeValueSnapshot() {
        final double[] monitoredPercentiles = this.histogramConfig.getPercentiles();
        if (monitoredPercentiles.length == 0) {
            return null;
        }
        final ValueAtPercentile[] values = new ValueAtPercentile[monitoredPercentiles.length];
        for (int i = 0; i < monitoredPercentiles.length; i++) {
            final double p = monitoredPercentiles[i];
            values[i] = ValueAtPercentile.of(p, valueAtPercentile(this.accumulatedHistogram, p * 100));
        }
        return values;
    }

    private CountAtValue[] takeCountSnapshot(boolean supportsAggregablePercentiles) {
        if (!this.histogramConfig.isPublishingHistogram()) {
            return null;
        }
        final Set<Long> monitoredValues = this.histogramConfig.getHistogramBuckets(supportsAggregablePercentiles);
        if (monitoredValues.isEmpty()) {
            return null;
        }
        final CountAtValue[] counts = new CountAtValue[monitoredValues.size()];
        final Iterator<Long> iterator = monitoredValues.iterator();
        for (int i = 0; i < counts.length; i++) {
            final long v = iterator.next();
            counts[i] = CountAtValue.of(v, countAtValue(this.accumulatedHistogram, v));
        }
        return counts;
    }

    public final void recordLong(long value) {
        rotate();
        try {
            for (T bucket : this.ringBuffer) {
                recordLong(bucket, value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        } finally {
            this.accumulatedHistogramStale = true;
        }
    }

    public final void recordDouble(double value) {
        rotate();
        try {
            for (T bucket : this.ringBuffer) {
                recordDouble(bucket, value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram cannot be extended to include it
        } finally {
            this.accumulatedHistogramStale = true;
        }
    }

    private void rotate() {
        long timeSinceLastRotateMillis = this.clock.wallTime() - this.lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis < this.durationBetweenRotatesMillis) {
            // Need to wait more for next rotation.
            return;
        }
        if (!rotatingUpdater.compareAndSet(this, 0, 1)) {
            // Being rotated by other thread already.
            return;
        }
        try {
            synchronized (this) {
                do {
                    resetBucket(this.ringBuffer[this.currentBucket]);
                    if (++this.currentBucket >= this.ringBuffer.length) {
                        this.currentBucket = 0;
                    }
                    timeSinceLastRotateMillis -= this.durationBetweenRotatesMillis;
                    this.lastRotateTimestampMillis += this.durationBetweenRotatesMillis;
                } while (timeSinceLastRotateMillis >= this.durationBetweenRotatesMillis);

                resetAccumulatedHistogram(this.accumulatedHistogram);
                this.accumulatedHistogramStale = true;
            }
        } finally {
            this.rotating = 0;
        }
    }

}
