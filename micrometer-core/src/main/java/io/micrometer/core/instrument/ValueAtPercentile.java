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

package io.micrometer.core.instrument;

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

public final class ValueAtPercentile {

    private final double percentile;

    private final double value;

    private ValueAtPercentile(double percentile, double value) {
        this.percentile = percentile;
        this.value = value;
    }

    public static ValueAtPercentile of(double percentile, double value) {
        return new ValueAtPercentile(percentile, value);
    }

    public double percentile() {
        return this.percentile;
    }

    public double value() {
        return this.value;
    }

    public double value(TimeUnit unit) {
        return TimeUtils.nanosToUnit(this.value, unit);
    }

    @Override
    public String toString() {
        return "(" + this.value + " at " + this.percentile * 100 + "%)";
    }

}
