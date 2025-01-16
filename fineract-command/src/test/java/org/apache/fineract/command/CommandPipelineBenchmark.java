/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.command;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.command.core.CommandRouter;
import org.apache.fineract.command.implementation.DefaultCommandPipeline;
import org.apache.fineract.command.implementation.DefaultCommandRouter;
import org.apache.fineract.command.implementation.DisruptorCommandExecutor;
import org.apache.fineract.command.sample.command.DummyCommand;
import org.apache.fineract.command.sample.handler.DummyCommandHandler;
import org.apache.fineract.command.sample.middleware.DummyIdempotencyMiddleware;
import org.apache.fineract.command.sample.middleware.DummyMiddleware;
import org.apache.fineract.command.sample.model.DummyRequest;
import org.apache.fineract.command.sample.model.DummyResponse;
import org.apache.fineract.command.sample.service.DefaultDummyService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@Slf4j
@BenchmarkMode(Mode.Throughput) // Measures operations per second
@Warmup(iterations = 2) // JVM warm-up iterations
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // Measurement iterations
@State(Scope.Thread) // Benchmark state for each thread
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Output results in milliseconds
@SuppressWarnings({ "raw" })
public class CommandPipelineBenchmark {

    private CommandRouter router;
    private Disruptor<DisruptorCommandExecutor.CommandEvent> disruptor;

    private CommandPipeline pipeline;

    @Setup(Level.Iteration)
    public void setUp() {
        this.router = new DefaultCommandRouter(List.of(new DummyCommandHandler(new DefaultDummyService())));

        // Create the disruptor
        this.disruptor = new Disruptor<>(DisruptorCommandExecutor.CommandEvent::new, 1024, DaemonThreadFactory.INSTANCE);
        // this.disruptor = new Disruptor(CommandEventFactory.class, 1024, DaemonThreadFactory.INSTANCE,
        // ProducerType.SINGLE, new BlockingWaitStrategy());

        disruptor.handleEventsWith(new DisruptorCommandExecutor.CompleteableCommandEventHandler(
                List.of(new DummyMiddleware(), new DummyIdempotencyMiddleware()), router));

        // Start the disruptor
        disruptor.start();

        pipeline = new DefaultCommandPipeline(new DisruptorCommandExecutor(disruptor));
    }

    @TearDown(Level.Iteration)
    public void tearDown() {}

    @Benchmark
    public void processCommand() {
        var command = new DummyCommand();
        command.setId(UUID.randomUUID());
        command.setPayload(DummyRequest.builder().content("hello").build());

        Supplier<DummyResponse> result = pipeline.send(command);

        // NOTE: force yield
        result.get();
    }
}
