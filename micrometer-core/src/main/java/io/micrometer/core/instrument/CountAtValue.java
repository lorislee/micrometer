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

/**
 * Data class to hold a {code value} and a {@code count}.
 *
 * @author Jon Schneider
 */
public final class CountAtValue {

    private final long value;

    private final double count;

    private CountAtValue(long value, double count) {
        this.value = value;
        this.count = count;
    }

    /**
     * Return a new {@link CountAtValue} with the specified {@code value} and {@code count}.
     * @param value the value in nanoseconds
     * @param count the count
     * @return a new {@link CountAtValue} instance
     */
    public static CountAtValue of(long value, double count) {
        return new CountAtValue(value, count);
    }

    /**
     * Return the contained value.
     * @return the value in nanoseconds
     */
    public long value() {
        return this.value;
    }

    /**
     * Return the value expressed in the given time unit.
     * @param unit the time unit
     * @return the value in converted to the specified time unit
     */
    public double value(TimeUnit unit) {
        return TimeUtils.nanosToUnit(this.value, unit);
    }

    /**
     * Return the contained count.
     * @return the count
     */
    public double count() {
        return this.count;
    }

    @Override
    public String toString() {
        return "(" + this.count + " at " + this.value + ')';
    }

}
