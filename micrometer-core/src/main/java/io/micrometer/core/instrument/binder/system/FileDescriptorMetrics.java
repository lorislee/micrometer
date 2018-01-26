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

package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.util.Collections.emptyList;

/**
 * File descriptor metrics.
 *
 * @author Michael Weirauch
 */
@NonNullApi
@NonNullFields
public class FileDescriptorMetrics implements MeterBinder {

    private final OperatingSystemMXBean osBean;

    private final Iterable<Tag> tags;

    @Nullable
    private final Method openFdsMethod;

    @Nullable
    private final Method maxFdsMethod;

    public FileDescriptorMetrics() {
        this(emptyList());
    }

    public FileDescriptorMetrics(Iterable<Tag> tags) {
        this(ManagementFactory.getOperatingSystemMXBean(), tags);
    }

    // VisibleForTesting
    FileDescriptorMetrics(OperatingSystemMXBean osBean, Iterable<Tag> tags) {
        this.osBean = osBean;
        this.tags = tags;
        this.openFdsMethod = detectMethod("getOpenFileDescriptorCount");
        this.maxFdsMethod = detectMethod("getMaxFileDescriptorCount");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (this.openFdsMethod != null) {
            Gauge.builder("process.open.fds", this.osBean, x -> invoke(this.openFdsMethod))
                .tags(this.tags)
                .description("The open file descriptor count")
                .register(registry);
        }
        if (this.maxFdsMethod != null) {
            Gauge.builder("process.max.fds", this.osBean, x -> invoke(this.maxFdsMethod))
                .tags(this.tags)
                .description("The maximum file descriptor count")
                .register(registry);
        }
    }

    private double invoke(@Nullable Method method) {
        try {
            return method != null ? (double) (long) method.invoke(this.osBean) : Double.NaN;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return Double.NaN;
        }
    }

    @Nullable
    private Method detectMethod(String name) {
        try {
            final Method method = this.osBean.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

}
