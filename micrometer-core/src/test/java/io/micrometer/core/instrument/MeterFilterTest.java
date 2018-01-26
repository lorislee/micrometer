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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.lang.Nullable;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static java.util.stream.StreamSupport.stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class MeterFilterTest {
    private static Condition<Meter.Id> tag(String tagKey) {
        return tag(tagKey, null);
    }

    private static Condition<Meter.Id> tag(String tagKey, @Nullable String tagValue) {
        return new Condition<>(
            id -> stream(id.getTags().spliterator(), false)
                .anyMatch(t -> t.getKey().equals(tagKey) && (tagValue == null || t.getValue().equals(tagValue))),
            "Must have a tag with key '" + tagKey + "'");
    }

    @Test
    void commonTags() {
        MeterFilter filter = MeterFilter.commonTags(Tags.zip("k2", "v2"));
        Meter.Id id = new Meter.Id("name", Tags.zip("k1", "v1"), null, null, Meter.Type.COUNTER);

        Meter.Id filteredId = filter.map(id);
        assertThat(filteredId).has(tag("k1", "v1"));
        assertThat(filteredId).has(tag("k2", "v2"));
    }

    @Test
    void ignoreTags() {
        MeterFilter filter = MeterFilter.ignoreTags("k1", "k2");
        Meter.Id id = new Meter.Id("name", Tags.zip("k1", "v1", "k2", "v2", "k3", "v3"), null, null, Meter.Type.COUNTER);

        Meter.Id filteredId = filter.map(id);
        assertThat(filteredId).has(tag("k3"));
        assertThat(filteredId).doesNotHave(tag("k1"));
        assertThat(filteredId).doesNotHave(tag("k2"));
    }

    @Test
    void replaceTagValues() {
        MeterFilter filter = MeterFilter.replaceTagValues("status", s -> s.charAt(0) + "xx", "200");

        Meter.Id id = new Meter.Id("name", Tags.zip("status", "400"), null, null, Meter.Type.COUNTER);
        Meter.Id filteredId = filter.map(id);
        assertThat(filteredId).has(tag("status", "4xx"));

        id = new Meter.Id("name", Tags.zip("status", "200"), null, null, Meter.Type.COUNTER);
        filteredId = filter.map(id);
        assertThat(filteredId).has(tag("status", "200"));
    }

    @Test
    @Issue("#329")
    void renameTags() {
        MeterFilter filter = MeterFilter.renameTag("hystrix", "group", "hystrixgroup");
        Meter.Id id = new Meter.Id("hystrix.something", Tags.zip("k", "v", "group", "mygroup"), null, null, Meter.Type.GAUGE);
        assertThat(filter.map(id)).has(tag("hystrixgroup", "mygroup"));

        Meter.Id id2 = new Meter.Id("something.else", Tags.zip("group", "mygroup"), null, null, Meter.Type.GAUGE);
        assertThat(filter.map(id2)).has(tag("group", "mygroup"));
    }

    @Test
    void maximumAllowableMetrics() {
        MeterFilter filter = MeterFilter.maximumAllowableMetrics(1);

        Meter.Id id = new Meter.Id("name", emptyList(), null, null, Meter.Type.COUNTER);
        Meter.Id id2 = new Meter.Id("name2", emptyList(), null, null, Meter.Type.COUNTER);

        assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(id)).isEqualTo(MeterFilterReply.NEUTRAL);
        assertThat(filter.accept(id2)).isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    void maximumAllowableTags() {
        AtomicInteger n = new AtomicInteger(0);

        MeterFilter filter = MeterFilter.maximumAllowableTags("name", "k", 2, new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                n.incrementAndGet();
                return MeterFilterReply.NEUTRAL;
            }
        });

        Meter.Id id = new Meter.Id("name", Tags.of("k", "1"), null, null, Meter.Type.COUNTER);
        Meter.Id id2 = new Meter.Id("name", Tags.of("k", "2"), null, null, Meter.Type.COUNTER);
        Meter.Id id3 = new Meter.Id("name", Tags.of("k", "3"), null, null, Meter.Type.COUNTER);

        filter.accept(id);
        filter.accept(id);
        filter.accept(id2);
        filter.accept(id);
        filter.accept(id3);

        assertThat(n.get()).isEqualTo(1);
    }
}
