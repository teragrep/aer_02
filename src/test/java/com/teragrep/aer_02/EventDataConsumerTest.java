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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.teragrep.aer_02.fakes.OutputFake;
import com.teragrep.aer_02.plugin.DefaultPluginFactory;
import com.teragrep.aer_02.plugin.WrappedPluginFactoryWithConfig;
import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.akv_01.event.ParsedEventFactory;
import com.teragrep.akv_01.event.UnparsedEventImpl;
import com.teragrep.akv_01.event.metadata.offset.EventOffsetImpl;
import com.teragrep.akv_01.event.metadata.partitionContext.EventPartitionContextImpl;
import com.teragrep.akv_01.event.metadata.properties.EventPropertiesImpl;
import com.teragrep.akv_01.event.metadata.systemProperties.EventSystemPropertiesImpl;
import com.teragrep.akv_01.event.metadata.time.EnqueuedTimeImpl;
import com.teragrep.akv_01.plugin.PluginFactoryConfigImpl;
import com.teragrep.nlf_01.NLFPluginFactory;
import jakarta.json.Json;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.codahale.metrics.MetricRegistry.name;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventDataConsumerTest {

    @Test
    public void testLatencyMetric() {
        Map<String, Object> partitionContext = new HashMap<>();
        partitionContext.put("FullyQualifiedNamespace", "eventhub.123");
        partitionContext.put("EventHubName", "test1");
        partitionContext.put("ConsumerGroup", "$Default");
        partitionContext.put("PartitionId", "0");
        Map<String, Object> props = new HashMap<>();
        Map<String, Object> systemProps = new HashMap<>();
        systemProps.put("SequenceNumber", "1");
        MetricRegistry metricRegistry = new MetricRegistry();
        EventDataConsumer eventDataConsumer = new EventDataConsumer(
                Logger.getAnonymousLogger(),
                new OutputFake(),
                new HashMap<>(),
                new WrappedPluginFactoryWithConfig(
                        new NLFPluginFactory(),
                        new PluginFactoryConfigImpl(NLFPluginFactory.class.getName(), "")
                ),
                new WrappedPluginFactoryWithConfig(
                        new DefaultPluginFactory(),
                        new PluginFactoryConfigImpl(
                                DefaultPluginFactory.class.getName(),
                                Json.createObjectBuilder().add("realHostname", "real").add("syslogHostname", "host").add("syslogAppname", "app").build().toString()
                        )
                ),
                metricRegistry
        );

        final double records = 10;
        List<ParsedEvent> parsedEvents = new ArrayList<>();
        for (int i = 0; i < records; i++) {
            if (i >= 5) {
                partitionContext.put("PartitionId", "1");
            }
            String enqueuedTime = LocalDateTime.now(ZoneId.of("Z")).minusSeconds(10).toString();
            parsedEvents
                    .add(
                            new ParsedEventFactory(
                                    new UnparsedEventImpl(
                                            "event",
                                            new EventPartitionContextImpl(new HashMap<>(partitionContext)),
                                            new EventPropertiesImpl(props),
                                            new EventSystemPropertiesImpl(systemProps),
                                            new EnqueuedTimeImpl(enqueuedTime),
                                            new EventOffsetImpl(String.valueOf(i))
                                    )
                            ).parsedEvent()
                    );
        }
        eventDataConsumer.accept(parsedEvents);

        // 5 records for each partition
        Gauge<Long> gauge1 = metricRegistry.gauge(name(EventDataConsumer.class, "latency-seconds", "0"));
        Gauge<Long> gauge2 = metricRegistry.gauge(name(EventDataConsumer.class, "latency-seconds", "1"));

        Assertions.assertEquals(10, gauge1.getValue());
        Assertions.assertEquals(10, gauge2.getValue());
    }
}
