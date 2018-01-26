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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.TimeDecayingMax;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Cumulative distribution summary.
 *
 * @author Clint Checketts
 * @author Vladimir Bukhtoyarov
 * @author Jon Schneider
 */
public class CumulativeDistributionSummary extends AbstractDistributionSummary {

    private final AtomicLong count;

    private final DoubleAdder total;

    private final TimeDecayingMax max;

    public CumulativeDistributionSummary(Id id, Clock clock, HistogramConfig histogramConfig) {
        super(id, clock, histogramConfig);
        this.count = new AtomicLong();
        this.total = new DoubleAdder();
        this.max = new TimeDecayingMax(clock, histogramConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        this.count.incrementAndGet();
        this.total.add(amount);
        this.max.record(amount);
    }

    @Override
    public long count() {
        return this.count.get();
    }

    @Override
    public double totalAmount() {
        return this.total.sum();
    }

    @Override
    public double max() {
        return this.max.poll();
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(this::totalAmount, Statistic.Total),
            new Measurement(this::max, Statistic.Max)
        );
    }

}
