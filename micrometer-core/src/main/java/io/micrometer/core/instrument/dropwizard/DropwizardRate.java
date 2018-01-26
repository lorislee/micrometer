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

package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.EWMA;
import io.micrometer.core.instrument.Clock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * For use in Dropwizard implementations of {@link io.micrometer.core.instrument.FunctionTimer}
 * and {@link io.micrometer.core.instrument.FunctionCounter}.
 */
class DropwizardRate {

	private static final long TICK_INTERVAL = TimeUnit.SECONDS.toNanos(5);

    private final AtomicLong lastTime;

    private final EWMA m1Rate = EWMA.oneMinuteEWMA();

    private final EWMA m5Rate = EWMA.fiveMinuteEWMA();

    private final EWMA m15Rate = EWMA.fifteenMinuteEWMA();

    private final Clock clock;

    DropwizardRate(Clock clock) {
        this.clock = clock;
        this.lastTime = new AtomicLong(clock.monotonicTime());
    }

    /**
     * @param increment Spread the increment out over the interval
     */
    private synchronized void tickIfNecessary(long increment) {
        final long oldTime = this.lastTime.get();
        final long currentTime = this.clock.monotonicTime();
        final long age = currentTime - oldTime;
        if (age > TICK_INTERVAL) {
            final long newIntervalStartTick = currentTime - age % TICK_INTERVAL;
            if (this.lastTime.compareAndSet(oldTime, newIntervalStartTick)) {
                final long requiredTicks = age / TICK_INTERVAL;

                // divide the increment equally over the interval to arrive at a reasonable approximation
                // of rate behavior in many cases (but not all)
                final long updateAtEachInterval = increment / requiredTicks;

                for (long i = 0; i < requiredTicks; i++) {
                    this.m1Rate.update(updateAtEachInterval);
                    this.m5Rate.update(updateAtEachInterval);
                    this.m15Rate.update(updateAtEachInterval);

                    this.m1Rate.tick();
                    this.m5Rate.tick();
                    this.m15Rate.tick();
                }

                final long updateRemainder = increment % requiredTicks;
                this.m1Rate.update(updateRemainder);
                this.m5Rate.update(updateRemainder);
                this.m15Rate.update(updateRemainder);
            }
        } else {
            this.m1Rate.update(increment);
            this.m5Rate.update(increment);
            this.m15Rate.update(increment);
        }
    }

    /**
     * Mimicks what happens inside of {@link com.codahale.metrics.Meter#mark(long)},
     * but ticks AFTER the increment.
     */
    public void increment(long n) {
        tickIfNecessary(n);
    }

    public double getOneMinuteRate() {
        tickIfNecessary(0);
        return this.m1Rate.getRate(TimeUnit.SECONDS);
    }

    public double getFifteenMinuteRate() {
        tickIfNecessary(0);
        return this.m15Rate.getRate(TimeUnit.SECONDS);
    }

    public double getFiveMinuteRate() {
        tickIfNecessary(0);
        return this.m5Rate.getRate(TimeUnit.SECONDS);
    }

}
