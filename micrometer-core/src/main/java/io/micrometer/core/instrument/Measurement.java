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

import java.util.function.Supplier;

/**
 * A measurement sampled from a meter.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 */
public class Measurement {

    private final Supplier<Double> f;

    private final Statistic statistic;

    public Measurement(Supplier<Double> f, Statistic statistic) {
        this.f = f;
        this.statistic = statistic;
    }

    /**
     * Value for the measurement.
     */
    public double getValue() {
        return this.f.get();
    }

    public Statistic getStatistic() {
        return this.statistic;
    }

    @Override
    public String toString() {
        return "Measurement{" +
            "statistic='" + this.statistic + '\'' +
            ", value=" + getValue() +
            '}';
    }

}
