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
package io.micrometer.datadog;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class DatadogMeterRegistry extends StepMeterRegistry {
    private final URL postTimeSeriesEndpoint;

    private final Logger logger = LoggerFactory.getLogger(DatadogMeterRegistry.class);
    private final DatadogConfig config;

    /**
     * Metric names for which we have posted metadata concerning type and base unit
     */
    private final Set<String> verifiedMetadata = ConcurrentHashMap.newKeySet();

    public DatadogMeterRegistry(DatadogConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public DatadogMeterRegistry(DatadogConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        this.config().namingConvention(new DatadogNamingConvention());

        try {
            this.postTimeSeriesEndpoint = URI.create(config.uri() + "/api/v1/series?api_key=" + config.apiKey()).toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        this.config = config;

        start(threadFactory);
    }

    @Override
    protected void publish() {
        Map<String, DatadogMetricMetadata> metadataToSend = new HashMap<>();

        try {
            HttpURLConnection con = null;

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                try {
                    con = (HttpURLConnection) postTimeSeriesEndpoint.openConnection();
                    con.setConnectTimeout((int) config.connectTimeout().toMillis());
                    con.setReadTimeout((int) config.readTimeout().toMillis());
                    con.setRequestMethod("POST");
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setDoOutput(true);

                    /*
                    Example post body from Datadog API docs. Host and tags are optional.
                    "{ \"series\" :
                            [{\"metric\":\"test.metric\",
                              \"points\":[[$currenttime, 20]],
                              \"host\":\"test.example.com\",
                              \"tags\":[\"environment:test\"]}
                            ]
                    }"
                    */

                    String body = "{\"series\":[" +
                        batch.stream().flatMap(m -> {
                            if (m instanceof Timer) {
                                return writeTimer((Timer) m, metadataToSend);
                            }
                            if (m instanceof DistributionSummary) {
                                return writeSummary((DistributionSummary) m, metadataToSend);
                            }
                            if (m instanceof FunctionTimer) {
                                return writeTimer((FunctionTimer) m, metadataToSend);
                            }
                            return writeMeter(m, metadataToSend);
                        }).collect(joining(",")) +
                        "]}";

                    try (OutputStream os = con.getOutputStream()) {
                        os.write(body.getBytes());
                        os.flush();
                    }

                    int status = con.getResponseCode();

                    if (status >= 200 && status < 300) {
                        logger.info("successfully sent " + batch.size() + " metrics to datadog");
                    } else if (status >= 400) {
                        try (InputStream in = con.getErrorStream()) {
                            logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                                .lines().collect(joining("\n")));
                        }
                    } else {
                        logger.error("failed to send metrics: http " + status);
                    }
                } finally {
                    quietlyCloseUrlConnection(con);
                }
            }
        } catch (Exception e) {
            logger.warn("failed to send metrics", e);
        }

        metadataToSend.forEach(this::postMetricMetadata);
    }

    private void quietlyCloseUrlConnection(@Nullable HttpURLConnection con) {
        try {
            if (con != null) {
                con.disconnect();
            }
        } catch (Exception ignore) {
        }
    }

    private Stream<String> writeTimer(FunctionTimer timer, Map<String, DatadogMetricMetadata> metadata) {
        long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();

        addToMetadataList(metadata, id, "count", Statistic.COUNT, "occurrence");
        addToMetadataList(metadata, id, "avg", Statistic.VALUE, null);
        addToMetadataList(metadata, id, "sum", Statistic.TOTAL_TIME, null);

        // we can't know anything about max and percentiles originating from a function timer
        return Stream.of(
            writeMetric(id, "count", wallTime, timer.count()),
            writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit())),
            writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<String> writeTimer(Timer timer, Map<String, DatadogMetricMetadata> metadata) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = timer.takeSnapshot(false);
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        metrics.add(writeMetric(id, "sum", wallTime, snapshot.total(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "count", wallTime, snapshot.count()));
        metrics.add(writeMetric(id, "avg", wallTime, snapshot.mean(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "max", wallTime, snapshot.max(getBaseTimeUnit())));

        addToMetadataList(metadata, id, "sum", Statistic.TOTAL_TIME, null);
        addToMetadataList(metadata, id, "count", Statistic.COUNT, "occurrence");
        addToMetadataList(metadata, id, "avg", Statistic.VALUE, null);
        addToMetadataList(metadata, id, "max", Statistic.MAX, null);

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            metrics.add(writeMetric(id, suffix, wallTime, v.value(getBaseTimeUnit())));

            addToMetadataList(metadata, id, suffix, Statistic.VALUE, null);
        }

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary, Map<String, DatadogMetricMetadata> metadata) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = summary.takeSnapshot(false);
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        metrics.add(writeMetric(id, "sum", wallTime, snapshot.total()));
        metrics.add(writeMetric(id, "count", wallTime, snapshot.count()));
        metrics.add(writeMetric(id, "avg", wallTime, snapshot.mean()));
        metrics.add(writeMetric(id, "max", wallTime, snapshot.max()));

        addToMetadataList(metadata, id, "sum", Statistic.TOTAL, null);
        addToMetadataList(metadata, id, "count", Statistic.COUNT, "occurrence");
        addToMetadataList(metadata, id, "avg", Statistic.VALUE, null);
        addToMetadataList(metadata, id, "max", Statistic.MAX, null);

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            metrics.add(writeMetric(id, suffix, wallTime, v.value()));

            addToMetadataList(metadata, id, suffix, Statistic.VALUE, null);
        }

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter m, Map<String, DatadogMetricMetadata> metadata) {
        long wallTime = clock.wallTime();
        return stream(m.measure().spliterator(), false)
            .map(ms -> {
                Meter.Id id = m.getId().withTag(ms.getStatistic());
                addToMetadataList(metadata, id, null, ms.getStatistic(), null);
                return writeMetric(id, null, wallTime, ms.getValue());
            });
    }

    private void addToMetadataList(Map<String, DatadogMetricMetadata> metadata, Meter.Id id, @Nullable String suffix,
                                   Statistic stat, @Nullable String overrideBaseUnit) {
        if (config.applicationKey() == null)
            return; // we can't set metadata correctly without the application key

        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        String metricName = getConventionName(fullId);
        if (!verifiedMetadata.contains(metricName)) {
            metadata.put(metricName, new DatadogMetricMetadata(fullId, stat, config.descriptions(), overrideBaseUnit));
        }
    }

    //VisibleForTesting
    String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        Iterable<Tag> tags = fullId.getTags();

        String host = config.hostTag() == null ? "" : stream(tags.spliterator(), false)
            .filter(t -> requireNonNull(config.hostTag()).equals(t.getKey()))
            .findAny()
            .map(t -> ",\"host\":\"" + t.getValue() + "\"")
            .orElse("");

        String tagsArray = tags.iterator().hasNext() ?
            ",\"tags\":[" +
                stream(tags.spliterator(), false)
                    .map(t -> "\"" + t.getKey() + ":" + t.getValue() + "\"")
                    .collect(joining(",")) + "]" : "";

        return "{\"metric\":\"" + getConventionName(fullId) + "\"," +
            "\"points\":[[" + (wallTime / 1000) + ", " + value + "]]" + host + tagsArray + "}";
    }

    /**
     * Set up metric metadata once per time series
     */
    private void postMetricMetadata(String metricName, DatadogMetricMetadata metadata) {
        // already posted the metadata for this metric
        if (verifiedMetadata.contains(metricName))
            return;

        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) URI.create(config.uri() + "/api/v1/metrics/" + metricName
                + "?api_key=" + config.apiKey() + "&application_key=" + config.applicationKey()).toURL().openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod("PUT");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            try (OutputStream os = con.getOutputStream()) {
                os.write(metadata.editMetadataBody().getBytes());
                os.flush();
            }

            int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                verifiedMetadata.add(metricName);
            } else if (status >= 400) {
                try (InputStream in = con.getErrorStream()) {
                    String msg = new BufferedReader(new InputStreamReader(in))
                        .lines().collect(joining("\n"));
                    if (msg.contains("metric_name not found")) {
                        // Do nothing. Metrics that are newly created in Datadog are not immediately available
                        // for metadata modification. We will keep trying this request on subsequent publishes,
                        // where it will eventually succeed.
                    } else {
                        logger.error("failed to send metric metadata: " + msg);
                    }
                }
            } else {
                logger.error("failed to send metric metadata: http " + status);
            }
        } catch (IOException e) {
            logger.warn("failed to send metric metadata", e);
        } finally {
            quietlyCloseUrlConnection(con);
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    /**
     * Copy tags, unit, and description from an existing id, but change the name.
     */
    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return new Meter.Id(id.getName() + "." + suffix, id.getTags(), id.getBaseUnit(), id.getDescription(), id.getType());
    }
}
