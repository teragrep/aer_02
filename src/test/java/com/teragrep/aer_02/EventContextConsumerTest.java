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

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.models.PartitionContext;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.teragrep.aer_02.config.MetricsConfig;
import com.teragrep.aer_02.config.source.PropertySource;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.aer_02.fakes.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;

import static com.codahale.metrics.MetricRegistry.name;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventContextConsumerTest {

    private final Sourceable configSource = new PropertySource();
    private final int prometheusPort = new MetricsConfig(configSource).prometheusPort;

    @Test
    public void testLatencyMetric() {
        MetricRegistry metricRegistry = new MetricRegistry();
        EventDataConsumer eventDataConsumer = new EventDataConsumer(
                configSource,
                new OutputFake(),
                metricRegistry,
                prometheusPort
        );

        final double records = 10;
        for (int i = 0; i < records; i++) {
            EventData eventData = new EventDataFake();
            PartitionContext partitionContext;
            if (i < 5) {
                partitionContext = new PartitionContext("namespace", "hub", "consumer", "1");
            }
            else {
                partitionContext = new PartitionContext("namespace", "hub", "consumer", "2");
            }
            eventDataConsumer.accept(eventData, partitionContext);
        }

        Assertions.assertDoesNotThrow(eventDataConsumer::close);

        long latency = Instant.now().getEpochSecond();

        // 5 records for each partition
        Gauge<Long> gauge1 = metricRegistry.gauge(name(EventDataConsumer.class, "latency-seconds", "1"));
        Gauge<Long> gauge2 = metricRegistry.gauge(name(EventDataConsumer.class, "latency-seconds", "2"));

        // hard to test the exact correct latency
        Assertions.assertTrue(gauge1.getValue() >= latency);
        Assertions.assertTrue(gauge2.getValue() >= latency);
    }

    /*  @Test
    public void testDepthBytesMetric() {
        EventContextFactory eventContextFactory = new CheckpointlessEventContextFactory();
        MetricRegistry metricRegistry = new MetricRegistry();
    
        long depth1 = 0L;
        final double records = 10;
        EventData eventData = new EventDataFake();
    
        EventDataConsumer eventDataConsumer = new EventDataConsumer(configSource, new OutputFake(), metricRegistry, prometheusPort);
        eventDataConsumer.accept(eventData, null);
    
        for (int i = 1; i < records; i++) { // records - 1 loops
            if (i == 5) { // 5 records per partition
                depth1 = eventContext.getLastEnqueuedEventProperties().getOffset() - eventContext.getEventData().getOffset();
            }
    
            eventContextConsumer.accept(eventContext);
        }
    
        Assertions.assertDoesNotThrow(eventContextConsumer::close);
    
        long depth2 = eventContext.getLastEnqueuedEventProperties().getOffset() - eventContext.getEventData().getOffset();
        Gauge<Long> gauge1 = metricRegistry.gauge(name(EventContextConsumer.class, "depth-bytes", "1"));
        Gauge<Long> gauge2 = metricRegistry.gauge(name(EventContextConsumer.class, "depth-bytes", "2"));
    
        Assertions.assertEquals(depth1, 99L); // offsets are defined in the factory
        Assertions.assertEquals(depth2, 99L);
        Assertions.assertEquals(depth1, gauge1.getValue());
        Assertions.assertEquals(depth2, gauge2.getValue());
    } */

    @Test
    public void testEstimatedDataDepthMetric() {
        MetricRegistry metricRegistry = new MetricRegistry();
        EventDataConsumer eventDataConsumer = new EventDataConsumer(
                configSource,
                new OutputFake(),
                metricRegistry,
                prometheusPort
        );

        final double records = 10;
        long length = 0L;
        for (int i = 0; i < records; i++) {
            EventData data = new EventDataFake();
            length = length + data.getBody().length;
            PartitionContext partitionContext;
            if (i < 5) {
                partitionContext = new PartitionContext("namespace", "hub", "consumer", "1");
            }
            else {
                partitionContext = new PartitionContext("namespace", "hub", "consumer", "2");
            }
            eventDataConsumer.accept(data, partitionContext);
        }

        Assertions.assertDoesNotThrow(eventDataConsumer::close);

        Gauge<Long> gauge = metricRegistry.gauge(MetricRegistry.name(EventDataConsumer.class, "estimated-data-depth"));
        Double estimatedDepth = (length / records) / records;

        Assertions.assertEquals(estimatedDepth, gauge.getValue());
    }
}
