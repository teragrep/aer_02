/*
 * Teragrep Eventhub Reader as an Azure Function
 * Copyright (C) 2024 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://github.com/teragrep/teragrep/blob/main/LICENSE>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.aer_02;

import com.teragrep.aer_02.fakes.ExecutionContextFake;
import com.teragrep.aer_02.fakes.PartitionContextFake;
import com.teragrep.aer_02.fakes.SystemPropsFake;
import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.net_01.server.Server;
import com.teragrep.net_01.server.ServerFactory;
import com.teragrep.rlo_06.RFC5424Frame;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unit test for SyslogBridge class.
 */
public final class SyslogBridgeTest {

    private Server server;
    private EventLoop eventLoop;
    private Thread eventLoopThread;
    private ExecutorService executorService;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setup() {
        this.executorService = Executors.newFixedThreadPool(1);
        Consumer<FrameContext> syslogConsumer = new Consumer<FrameContext>() {

            @Override
            public synchronized void accept(FrameContext frameContext) {
                messages.add(frameContext.relpFrame().payload().toString());
            }
        };

        Supplier<FrameDelegate> frameDelegateSupplier = () -> new DefaultFrameDelegate(syslogConsumer);

        EventLoopFactory eventLoopFactory = new EventLoopFactory();
        this.eventLoop = Assertions.assertDoesNotThrow(eventLoopFactory::create);

        this.eventLoopThread = new Thread(eventLoop);
        eventLoopThread.start();

        ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new PlainFactory(),
                new FrameDelegationClockFactory(frameDelegateSupplier)
        );
        this.server = Assertions.assertDoesNotThrow(() -> serverFactory.create(1601));
    }

    @AfterEach
    void teardown() {
        eventLoop.stop();
        Assertions.assertDoesNotThrow(() -> eventLoopThread.join());
        executorService.shutdown();
        Assertions.assertDoesNotThrow(() -> server.close());
        messages.clear();
    }

    @Test
    void testSyslogBridge() {
        PartitionContextFake pcf = new PartitionContextFake("eventhub.123", "test1", "$Default", "0");
        Map<String, Object> props = new HashMap<>();
        final SyslogBridge bridge = new SyslogBridge();

        bridge.eventHubTriggerToSyslog(new String[] {
                "event0", "event1", "event2"
        }, pcf.asMap(), new Map[] {
                props, props, props
        }, new Map[] {
                new SystemPropsFake("0").asMap(), new SystemPropsFake("1").asMap(), new SystemPropsFake("2").asMap()
        }, Arrays.asList("2010-01-01T00:00:00", "2010-01-02T00:00:00", "2010-01-03T00:00:00"),
                Arrays.asList("0", "1", "2"), new ExecutionContextFake()
        );

        Assertions.assertEquals(3, messages.size());

        int loops = 0;
        for (String message : messages) {
            final RFC5424Frame frame = new RFC5424Frame(false);
            frame.load(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
            Assertions.assertTrue(Assertions.assertDoesNotThrow(frame::next));
            Assertions.assertEquals("localhost.localdomain", frame.hostname.toString());
            Assertions.assertEquals("aer-02", frame.appName.toString());
            Assertions.assertEquals(String.valueOf(loops), frame.msgId.toString());
            loops++;
        }

        Assertions.assertEquals(3, loops);
    }
}
