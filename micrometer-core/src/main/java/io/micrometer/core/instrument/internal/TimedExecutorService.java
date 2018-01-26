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

package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static java.util.stream.Collectors.toList;

/**
 * An {@link java.util.concurrent.ExecutorService} that is timed
 *
 * @author Jon Schneider
 */
public class TimedExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    private final Timer timer;

    public TimedExecutorService(MeterRegistry registry, ExecutorService delegate, String name, Iterable<Tag> tags) {
        this.delegate = delegate;
        this.timer = registry.timer(name, tags);
    }

    @Override
    public void shutdown() {
        this.delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return this.delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return this.delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return this.delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return this.delegate.submit(this.timer.wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return this.delegate.submit(() -> this.timer.record(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return this.delegate.submit(() -> this.timer.record(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return this.delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return this.delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return this.delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return this.delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        this.delegate.execute(this.timer.wrap(command));
    }

    private <T> Collection<? extends Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream().map(this.timer::wrap).collect(toList());
    }

}
