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

import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used to measure the rate of change based on calls to increment.
 */
public interface Counter extends Meter {
    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Update the counter by one.
     */
    default void increment() {
        increment(1.0);
    }

    /**
     * Update the counter by {@code amount}.
     *
     * @param amount Amount to add to the counter.
     */
    void increment(double amount);

    /**
     * The cumulative count since this counter was created.
     */
    double count();

    @Override
    default Iterable<Measurement> measure() {
        return Collections.singletonList(new Measurement(this::count, Statistic.Count));
    }

    @Override
    default Type type() {
        return Type.Counter;
    }

    class Builder {
        private final String name;
        private final List<Tag> tags = new ArrayList<>();
        @Nullable
        private String description;
        @Nullable
        private String baseUnit;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder tag(String key, String value) {
            tags.add(Tag.of(key, value));
            return this;
        }

        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        public Builder baseUnit(@Nullable String unit) {
            this.baseUnit = unit;
            return this;
        }

        public Counter register(MeterRegistry registry) {
            return registry.counter(new Meter.Id(name, tags, baseUnit, description, Type.Counter));
        }
    }
}
