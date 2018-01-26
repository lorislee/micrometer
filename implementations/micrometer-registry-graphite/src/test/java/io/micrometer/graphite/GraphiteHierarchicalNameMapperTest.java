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
package io.micrometer.graphite;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphiteHierarchicalNameMapperTest {
    @Test
    void tagsAsPrefix() {
        GraphiteHierarchicalNameMapper nameMapper = new GraphiteHierarchicalNameMapper("application");
        Meter.Id id = new Meter.Id("my.name", Tags.zip("application", "MYAPP", "other", "value"), null, null, Meter.Type.COUNTER);

        assertThat(nameMapper.toHierarchicalName(id, NamingConvention.camelCase))
            .isEqualTo("MYAPP.myName.other.value");
    }
}