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

package io.micrometer.core.instrument.binder.tomcat;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import org.apache.catalina.Manager;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @author Clint Checketts
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class TomcatMetrics implements MeterBinder {

    @Nullable
    private final Manager manager;

    private final MBeanServer mBeanServer;

    private final Iterable<Tag> tags;

    public TomcatMetrics(@Nullable Manager manager, Iterable<Tag> tags) {
        this(manager, tags, getMBeanServer());
    }

    public TomcatMetrics(@Nullable Manager manager, Iterable<Tag> tags, MBeanServer mBeanServer) {
        this.tags = tags;
        this.manager = manager;
        this.mBeanServer = mBeanServer;
    }

    public static void monitor(MeterRegistry meterRegistry, @Nullable Manager manager, String... tags) {
        monitor(meterRegistry, manager, Tags.zip(tags));
    }

    public static void monitor(MeterRegistry meterRegistry, @Nullable Manager manager, Iterable<Tag> tags) {
        new TomcatMetrics(manager, tags).bindTo(meterRegistry);
    }

    public static MBeanServer getMBeanServer() {
        List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
        if (!mBeanServers.isEmpty()) {
            return mBeanServers.get(0);
        }
        return ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public void bindTo(MeterRegistry reg) {
        registerGlobalRequestMetrics(reg);
        registerServletMetrics(reg);
        registerCacheMetrics(reg);
        registerThreadPoolMetrics(reg);
        if (this.manager == null) {
            // If the binder is created but unable to find the session manager don't register those metrics
            return;
        }
        Gauge.builder("tomcat.sessions.active.max", this.manager, Manager::getMaxActive)
            .tags(this.tags)
            .register(reg);
        Gauge.builder("tomcat.sessions.active.current", this.manager, Manager::getActiveSessions)
            .tags(this.tags)
            .register(reg);
        FunctionCounter.builder("tomcat.sessions.created", this.manager, Manager::getSessionCounter)
            .tags(this.tags)
            .register(reg);
        FunctionCounter.builder("tomcat.sessions.expired", this.manager, Manager::getExpiredSessions)
            .tags(this.tags)
            .register(reg);
        FunctionCounter.builder("tomcat.sessions.rejected", this.manager, Manager::getRejectedSessions)
            .tags(this.tags)
            .register(reg);
        TimeGauge.builder("tomcat.sessions.alive.max", this.manager, TimeUnit.SECONDS, Manager::getSessionMaxAliveTime)
            .tags(this.tags)
            .register(reg);
    }

    private void registerThreadPoolMetrics(MeterRegistry reg) {
        registerMetricsEventually("type", "ThreadPool", (name, allTags) -> {
            Gauge.builder("tomcat.threads.config.max", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "maxThreads")))
                .tags(allTags)
                .register(reg);

            Gauge.builder("tomcat.threads.busy", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "currentThreadsBusy")))
                .tags(allTags)
                .register(reg);

            Gauge.builder("tomcat.threads.current", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "currentThreadCount")))
                .tags(allTags)
                .register(reg);
        });
    }

    private void registerCacheMetrics(MeterRegistry reg) {
        registerMetricsEventually("type", "StringCache", (name, allTags) -> {
            FunctionCounter.builder("tomcat.cache.access", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "accessCount")))
                .tags(allTags)
                .register(reg);

            FunctionCounter.builder("tomcat.cache.hit", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "hitCount")))
                .tags(allTags)
                .register(reg);
        });
    }

    private void registerServletMetrics(MeterRegistry reg) {
        registerMetricsEventually("j2eeType", "Servlet", (name, allTags) -> {
            FunctionCounter.builder("tomcat.servlet.error", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "errorCount")))
                .tags(allTags)
                .register(reg);

            FunctionTimer.builder("tomcat.servlet.request", this.mBeanServer,
                s -> safeLong(() -> s.getAttribute(name, "requestCount")),
                s -> safeDouble(() -> s.getAttribute(name, "processingTime")), TimeUnit.MILLISECONDS)
                .tags(allTags)
                .register(reg);

            TimeGauge.builder("tomcat.servlet.request.max", this.mBeanServer, TimeUnit.MILLISECONDS,
                s -> safeDouble(() -> s.getAttribute(name, "maxTime")))
                .tags(allTags)
                .register(reg);
        });
    }

    private void registerGlobalRequestMetrics(MeterRegistry reg) {
        registerMetricsEventually("type", "GlobalRequestProcessor", (name, allTags) -> {
            FunctionCounter.builder("tomcat.global.sent", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "bytesSent")))
                .tags(allTags)
                .baseUnit("bytes")
                .register(reg);
            FunctionCounter.builder("tomcat.global.received", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "bytesReceived")))
                .tags(allTags)
                .baseUnit("bytes")
                .register(reg);
            FunctionCounter.builder("tomcat.global.error", this.mBeanServer,
                s -> safeDouble(() -> s.getAttribute(name, "errorCount")))
                .tags(allTags)
                .register(reg);
            FunctionTimer.builder("tomcat.global.request", this.mBeanServer,
                s -> safeLong(() -> s.getAttribute(name, "requestCount")),
                s -> safeDouble(() -> s.getAttribute(name, "processingTime")), TimeUnit.MILLISECONDS)
                .tags(allTags)
                .register(reg);
            TimeGauge.builder("tomcat.global.request.max", this.mBeanServer, TimeUnit.MILLISECONDS,
                s -> safeDouble(() -> s.getAttribute(name, "maxTime")))
                .tags(allTags)
                .register(reg);
        });
    }

    /**
     * If the MBean already exists, register metrics immediately. Otherwise register an MBean registration listener
     * with the MBeanServer and register metrics when/if the MBean becomes available.
     */
    private void registerMetricsEventually(String key, String value, BiConsumer<ObjectName, Iterable<Tag>> perObject) {
        try {
            Set<ObjectName> objs = this.mBeanServer.queryNames(new ObjectName("Tomcat:" + key + "=" + value + ",*"), null);
            if (!objs.isEmpty()) {
                // MBean is present, so we can register metrics now.
                objs.forEach(o -> perObject.accept(o, Tags.concat(this.tags, nameTag(o))));
                return;
            }
        } catch (MalformedObjectNameException e) {
            // should never happen
            throw new RuntimeException("Error registering Tomcat JMX based metrics", e);
        }

        // MBean isn't yet registered, so we'll set up a notification to wait for them to be present and register
        // metrics later.
        NotificationListener notificationListener = (notification, handback) -> {
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            ObjectName obj = mbs.getMBeanName();
            perObject.accept(obj, Tags.concat(this.tags, nameTag(obj)));
        };

        NotificationFilter filter = (NotificationFilter) notification -> {
            if (!MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType()))
                return false;

            // we can safely downcast now
            ObjectName obj = ((MBeanServerNotification) notification).getMBeanName();
            return obj.getDomain().equals("Tomcat") && obj.getKeyProperty(key).equals(value);

        };

        try {
            this.mBeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, notificationListener, filter, null);
        } catch (InstanceNotFoundException e) {
            // should never happen
            throw new RuntimeException("Error registering MBean listener", e);
        }
    }

    private double safeDouble(Callable<Object> callable) {
        try {
            return Double.parseDouble(callable.call().toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long safeLong(Callable<Object> callable) {
        try {
            return Long.parseLong(callable.call().toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private Iterable<Tag> nameTag(ObjectName name) {
        if (name.getKeyProperty("name") != null) {
            return Tags.zip("name", name.getKeyProperty("name").replaceAll("\"", ""));
        } else {
            return Collections.emptyList();
        }
    }

}
