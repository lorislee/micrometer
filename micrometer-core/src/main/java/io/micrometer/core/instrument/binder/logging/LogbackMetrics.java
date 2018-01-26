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

package io.micrometer.core.instrument.binder.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import static java.util.Collections.emptyList;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class LogbackMetrics implements MeterBinder {

	private final Iterable<Tag> tags;

    public LogbackMetrics() {
        this(emptyList());
    }

    public LogbackMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.addTurboFilter(new MetricsTurboFilter(registry, this.tags));
    }

}

@NonNullApi
@NonNullFields
class MetricsTurboFilter extends TurboFilter {

    private final Counter errorCounter;

    private final Counter warnCounter;

    private final Counter infoCounter;

    private final Counter debugCounter;

    private final Counter traceCounter;

    MetricsTurboFilter(MeterRegistry registry, Iterable<Tag> tags) {
        this.errorCounter = Counter.builder("logback.events")
            .tags(tags).tags("level", "error")
            .description("Number of error level events that made it to the logs")
            .register(registry);

        this.warnCounter = Counter.builder("logback.events")
            .tags(tags).tags("level", "warn")
            .description("Number of warn level events that made it to the logs")
            .register(registry);

        this.infoCounter = Counter.builder("logback.events")
            .tags(tags).tags("level", "info")
            .description("Number of info level events that made it to the logs")
            .register(registry);

        this.debugCounter = Counter.builder("logback.events")
            .tags(tags).tags("level", "debug")
            .description("Number of debug level events that made it to the logs")
            .register(registry);

        this.traceCounter = Counter.builder("logback.events")
            .tags(tags).tags("level", "trace")
            .description("Number of trace level events that made it to the logs")
            .register(registry);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        // cannot use logger.isEnabledFor(level), as it would cause a StackOverflowError by calling this filter again!
        if (level.isGreaterOrEqual(logger.getEffectiveLevel()) && format != null) {
            switch (level.toInt()) {
                case Level.ERROR_INT:
                    this.errorCounter.increment();
                    break;
                case Level.WARN_INT:
                    this.warnCounter.increment();
                    break;
                case Level.INFO_INT:
                    this.infoCounter.increment();
                    break;
                case Level.DEBUG_INT:
                    this.debugCounter.increment();
                    break;
                case Level.TRACE_INT:
                    this.traceCounter.increment();
                    break;
            }
        }
        return FilterReply.NEUTRAL;
    }

}
