/*
 * Teragrep syslog bridge function for Microsoft Azure EventHub
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

import com.microsoft.azure.functions.*;
import com.teragrep.aer_02.fakes.ExecutionContextFake;
import com.teragrep.aer_02.fakes.HttpResponseMessageBuilderFake;
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
import jakarta.json.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Unit test for SyslogBridge class.
 */
public final class SyslogBridgeTest {

    private Server server;
    private EventLoop eventLoop;
    private Thread eventLoopThread;
    private ExecutorService executorService;
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setup() {
        messages.clear();
        this.executorService = Executors.newFixedThreadPool(1);
        Consumer<FrameContext> syslogConsumer = new Consumer<>() {

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

            final Map<String, Map<String, String>> sdElems = frame.structuredData.sdElements
                    .stream()
                    .collect(
                            Collectors
                                    .toMap(
                                            (sde -> sde.sdElementId.toString()),
                                            (sde -> sde.sdParams
                                                    .stream()
                                                    .collect(
                                                            Collectors
                                                                    .toMap(
                                                                            sdp -> sdp.sdParamKey.toString(),
                                                                            sdp -> sdp.sdParamValue.toString()
                                                                    )
                                                    ))
                                    )
                    );

            Assertions
                    .assertEquals(
                            "{\\\"aer-02-exception\\\":\\\"com.teragrep.akv_01.plugin.PluginException: jakarta.json.JsonException: Event was not a JSON structure\\\"}",
                            sdElems.get("aer_02_event@48577").get("properties")
                    );

            loops++;
        }

        Assertions.assertEquals(3, loops);
    }

    @Test
    void testSyslogBridgeWithJsonRecordsData() {
        // This should use the NLFPlugin
        PartitionContextFake pcf = new PartitionContextFake("eventhub.123", "test1", "$Default", "0");
        Map<String, Object> props = new HashMap<>();
        final SyslogBridge bridge = new SyslogBridge();

        final String jsonRecords = Json
                .createObjectBuilder()
                .add(
                        "records",
                        Json
                                .createArrayBuilder()
                                .add(Json.createObjectBuilder().add("TimeGenerated", "2020-01-01T00:00:00.000Z").add("_ResourceId", "/1/2/3/4/5/6/7/8").add("AppRoleName", "app-role-name").add("Type", "AppTraces")).add(Json.createObjectBuilder().add("TimeGenerated", "2021-01-01T00:00:00.000Z").add("_ResourceId", "/1/2/3/4/5/6/7/8").add("AppRoleName", "app-role-name").add("Type", "AppTraces")).add(Json.createObjectBuilder().add("TimeGenerated", "2022-01-01T00:00:00.000Z").add("_ResourceId", "/1/2/3/4/5/6/7/8").add("AppRoleName", "app-role-name").add("Type", "AppTraces")).build()
                )
                .build()
                .toString();

        bridge.eventHubTriggerToSyslog(new String[] {
                jsonRecords, jsonRecords, jsonRecords
        }, pcf.asMap(), new Map[] {
                props, props, props
        }, new Map[] {
                new SystemPropsFake("0").asMap(), new SystemPropsFake("1").asMap(), new SystemPropsFake("2").asMap()
        }, Arrays.asList("2010-01-01T00:00:00", "2010-01-02T00:00:00", "2010-01-03T00:00:00"),
                Arrays.asList("0", "1", "2"), new ExecutionContextFake()
        );

        // there are 3 JSON records-type events with 3 records each, totalling 9 messages
        Assertions.assertEquals(9, messages.size());

        final String[] expectedSeqNums = new String[] {
                "0", "0", "0", "1", "1", "1", "2", "2", "2"
        };

        final String[] expectedMessages = new String[] {
                "{\"TimeGenerated\":\"2020-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2021-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2022-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2020-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2021-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2022-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2020-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2021-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}",
                "{\"TimeGenerated\":\"2022-01-01T00:00:00.000Z\",\"_ResourceId\":\"/1/2/3/4/5/6/7/8\",\"AppRoleName\":\"app-role-name\",\"Type\":\"AppTraces\"}"
        };

        int loops = 0;
        for (String message : messages) {
            final RFC5424Frame frame = new RFC5424Frame(false);
            frame.load(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
            Assertions.assertTrue(Assertions.assertDoesNotThrow(frame::next));
            Assertions.assertEquals(expectedMessages[loops], frame.msg.toString());
            Assertions.assertEquals("md5-6f401cfe0a539a619fa9c17798d19925-8", frame.hostname.toString());
            Assertions.assertEquals("app-role-name", frame.appName.toString());
            Assertions.assertEquals(expectedSeqNums[loops], frame.msgId.toString());

            final Map<String, Map<String, String>> sdElems = frame.structuredData.sdElements
                    .stream()
                    .collect(
                            Collectors
                                    .toMap(
                                            (sde -> sde.sdElementId.toString()),
                                            (sde -> sde.sdParams
                                                    .stream()
                                                    .collect(
                                                            Collectors
                                                                    .toMap(
                                                                            sdp -> sdp.sdParamKey.toString(),
                                                                            sdp -> sdp.sdParamValue.toString()
                                                                    )
                                                    ))
                                    )
                    );
            Assertions.assertEquals("{}", sdElems.get("aer_02_event@48577").get("properties"));

            loops++;
        }

        Assertions.assertEquals(9, loops);
    }

    @Test
    void testSyslogBridgeMetrics() {
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

        HttpRequestMessage<Optional<String>> req = new HttpRequestMessage<>() {

            @Override
            public URI getUri() {
                return null;
            }

            @Override
            public HttpMethod getHttpMethod() {
                return HttpMethod.GET;
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "text/plain");
                return h;
            }

            @Override
            public Map<String, String> getQueryParameters() {
                Map<String, String> q = new HashMap<>();
                return q;
            }

            @Override
            public Optional<String> getBody() {
                return Optional.empty();
            }

            @Override
            public HttpResponseMessage.Builder createResponseBuilder(HttpStatus httpStatus) {
                return new HttpResponseMessageBuilderFake();
            }

            @Override
            public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType httpStatusType) {
                return new HttpResponseMessageBuilderFake();
            }
        };

        HttpResponseMessage resp = bridge.metrics(req, new ExecutionContextFake());
        List<String> responseLines = Arrays.asList(resp.getBody().toString().split("\n"));

        Assertions.assertEquals(36, responseLines.size());
        // Check all metrics are present
        Assertions
                .assertTrue(
                        responseLines
                                .contains("# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___resends gauge")
                );
        Assertions
                .assertTrue(
                        responseLines
                                .contains(
                                        "# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___connectLatency summary"
                                )
                );
        Assertions
                .assertTrue(
                        responseLines
                                .contains(
                                        "# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___retriedConnects gauge"
                                )
                );
        Assertions
                .assertTrue(
                        responseLines
                                .contains(
                                        "# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___sendLatency summary"
                                )
                );
        Assertions
                .assertTrue(
                        responseLines
                                .contains("# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___records gauge")
                );
        Assertions
                .assertTrue(
                        responseLines.contains("# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___bytes gauge")
                );
        Assertions
                .assertTrue(
                        responseLines
                                .contains("# TYPE com_teragrep_aer_02_DefaultOutput___defaultOutput___connects gauge")
                );
        Assertions
                .assertTrue(
                        responseLines.contains("# TYPE com_teragrep_aer_02_EventDataConsumer_latency_seconds_0 gauge")
                );
    }
}
