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

package io.micrometer.core.annotation;

import java.lang.annotation.*;

import io.micrometer.core.instrument.LongTaskTimer;

/**
 * Indicates that a type or method should have time metrics recorded for each
 * method invocation.
 *
 * @author Jon Schneider
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Repeatable(TimedSet.class)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Timed {

	/**
	 * The name of the metric that should be produced. When not specific a
	 * default metric name will be used.1
	 * @return the name of the metric
	 */
    String value() default "";

    /**
     * Any additional tags that should me produced with the metrics.
     * @return any additional tags
     */
    String[] extraTags() default {};

    /**
     * If the metric should be produced using a {@link LongTaskTimer long task}.
     * @return if a long task timer should be used
     */
    boolean longTask() default false;

    /**
     * Any additional time series percentiles that should be produced.
     * @return any additional percentiles
     */
    double[] percentiles() default {};

    /**
	 * If a histogram bucket usable for generating aggregable percentile
	 * approximations should be produced.
	 * @return if a histogram bucket is produced
	 */
    boolean histogram() default false;

    /**
	 * Any additional description meta-data that should be attached to the
	 * produced metric.
	 * @return an additional description
	 */
    String description() default "";

}
