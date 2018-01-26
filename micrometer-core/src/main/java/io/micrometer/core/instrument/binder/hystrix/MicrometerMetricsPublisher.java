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

package io.micrometer.core.instrument.binder.hystrix;

import com.netflix.hystrix.*;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

/**
 * @author Clint Checketts
 */
@NonNullApi
@NonNullFields
public class MicrometerMetricsPublisher extends HystrixMetricsPublisher {

    private final MeterRegistry registry;

    public MicrometerMetricsPublisher(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey,
                                                                        HystrixCommandGroupKey commandGroupKey,
                                                                        HystrixCommandMetrics metrics,
                                                                        HystrixCircuitBreaker circuitBreaker,
                                                                        HystrixCommandProperties properties) {
        return new MicrometerMetricsPublisherCommand(registry, commandKey, commandGroupKey, metrics, circuitBreaker, properties);
    }
}
