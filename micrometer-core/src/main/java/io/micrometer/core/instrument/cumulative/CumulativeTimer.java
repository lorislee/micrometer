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

package io.micrometer.core.instrument.cumulative;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.histogram.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeDecayingMax;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jon Schneider
 */
public class CumulativeTimer extends AbstractTimer {

    private final AtomicLong count;

    private final AtomicLong total;

    private final TimeDecayingMax max;

    /**
     * Create a new instance.
     */
    public CumulativeTimer(Id id, Clock clock, HistogramConfig histogramConfig, PauseDetector pauseDetector, TimeUnit baseTimeUnit) {
        super(id, clock, histogramConfig, pauseDetector, baseTimeUnit);
        this.count = new AtomicLong();
        this.total = new AtomicLong();
        this.max = new TimeDecayingMax(clock, histogramConfig);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        long nanoAmount = (long) TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS);
        this.count.getAndAdd(1);
        this.total.getAndAdd(nanoAmount);
        this.max.record(nanoAmount, TimeUnit.NANOSECONDS);
    }

    @Override
    public long count() {
        return this.count.get();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.nanosToUnit(this.total.get(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return this.max.poll(unit);
    }

}
