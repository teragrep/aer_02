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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.teragrep.aer_02.config.RelpConnectionConfig;
import com.teragrep.aer_02.config.SyslogConfig;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;
import com.teragrep.rlp_01.client.SSLContextSupplier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static com.codahale.metrics.MetricRegistry.name;

final class EventDataConsumer implements AutoCloseable {

    // Note: Checkpointing is handled automatically.
    private final Output output;
    private final String realHostName;
    private final SyslogConfig syslogConfig;
    private final MetricRegistry metricRegistry;

    EventDataConsumer(
            final Sourceable configSource,
            final String hostname,
            final MetricRegistry metricRegistry,
            final SSLContextSupplier sslContextSupplier
    ) {
        this(
                configSource,
                new DefaultOutput("defaultOutput", new RelpConnectionConfig(configSource), metricRegistry, sslContextSupplier), hostname, metricRegistry
        );
    }

    EventDataConsumer(final Sourceable configSource, final String hostname, final MetricRegistry metricRegistry) {
        this(
                configSource,
                new DefaultOutput("defaultOutput", new RelpConnectionConfig(configSource), metricRegistry),
                hostname,
                metricRegistry
        );
    }

    EventDataConsumer(
            final Sourceable configSource,
            final Output output,
            final String hostname,
            final MetricRegistry metricRegistry
    ) {
        this.metricRegistry = metricRegistry;
        this.output = output;
        this.realHostName = hostname;
        this.syslogConfig = new SyslogConfig(configSource);
    }

    public void accept(
            String eventData,
            Map<String, Object> partitionContext,
            ZonedDateTime enqueuedTime,
            String offset,
            Map<String, Object> props,
            Map<String, Object> systemProps
    ) {

        String partitionId = String.valueOf(partitionContext.get("PartitionId"));
        metricRegistry.gauge(name(EventDataConsumer.class, "latency-seconds", partitionId), () -> new Gauge<Long>() {

            @Override
            public Long getValue() {
                return Instant.now().getEpochSecond() - enqueuedTime.toInstant().getEpochSecond();
            }
        });

        SDElement sdId = new SDElement("event_id@48577")
                .addSDParam("uuid", UUID.randomUUID().toString())
                .addSDParam("hostname", realHostName)
                .addSDParam("unixtime", Instant.now().toString())
                .addSDParam("id_source", "aer_02");

        SDElement sdPartition = new SDElement("aer_02_partition@48577")
                .addSDParam(
                        "fully_qualified_namespace",
                        String.valueOf(partitionContext.getOrDefault("FullyQualifiedNamespace", ""))
                )
                .addSDParam("eventhub_name", String.valueOf(partitionContext.getOrDefault("EventHubName", "")))
                .addSDParam("partition_id", String.valueOf(partitionContext.getOrDefault("PartitionId", "")))
                .addSDParam("consumer_group", String.valueOf(partitionContext.getOrDefault("ConsumerGroup", "")));

        String partitionKey = String.valueOf(systemProps.getOrDefault("PartitionKey", ""));

        SDElement sdEvent = new SDElement("aer_02_event@48577")
                .addSDParam("offset", offset == null ? "" : offset)
                .addSDParam("enqueued_time", enqueuedTime == null ? "" : enqueuedTime.toString())
                .addSDParam("partition_key", partitionKey == null ? "" : partitionKey);
        props.forEach((key, value) -> sdEvent.addSDParam("property_" + key, value.toString()));

        SDElement sdComponentInfo = new SDElement("aer_02@48577")
                .addSDParam("timestamp_source", enqueuedTime == null ? "generated" : "timeEnqueued");

        SyslogMessage syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withTimestamp(
                        enqueuedTime == null ? Instant.now().toEpochMilli() : enqueuedTime.toInstant().toEpochMilli()
                )
                .withHostname(syslogConfig.hostName())
                .withAppName(syslogConfig.appName())
                .withSDElement(sdId)
                .withSDElement(sdPartition)
                .withSDElement(sdEvent)
                .withSDElement(sdComponentInfo)
                .withMsgId(String.valueOf(systemProps.getOrDefault("SequenceNumber", "0")))
                .withMsg(eventData);

        output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        output.close();
    }
}
