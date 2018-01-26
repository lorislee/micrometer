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

package io.micrometer.core.instrument.dropwizard;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.util.TimeDecayingMax;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * @author Jon Schneider
 */
public class DropwizardDistributionSummary extends AbstractDistributionSummary {

    private final com.codahale.metrics.Histogram impl;

    private final DoubleAdder totalAmount = new DoubleAdder();

    private final TimeDecayingMax max;

    DropwizardDistributionSummary(Id id, Clock clock, com.codahale.metrics.Histogram impl, HistogramConfig histogramConfig) {
        super(id, clock, histogramConfig);
        this.impl = impl;
        this.max = new TimeDecayingMax(clock, histogramConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        if (amount >= 0) {
            this.impl.update((long) amount);
            this.totalAmount.add(amount);
            this.max.record(amount);
        }
    }

    @Override
    public long count() {
        return this.impl.getCount();
    }

    @Override
    public double totalAmount() {
        return this.totalAmount.doubleValue();
    }

    @Override
    public double max() {
        return this.max.poll();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(@Nullable Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

}
