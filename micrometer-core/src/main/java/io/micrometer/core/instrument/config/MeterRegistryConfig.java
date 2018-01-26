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

package io.micrometer.core.instrument.config;

import io.micrometer.core.lang.Nullable;

public interface MeterRegistryConfig {
    String prefix();

    /**
     * Get the value associated with a key.
     *
     * @param k Key to lookup in the config.
     * @return Value for the key or null if no key is present.
     */
    @Nullable
    String get(String k);
}
