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

import com.codahale.metrics.*;
import com.codahale.metrics.jmx.JmxReporter;
import com.teragrep.aer_02.config.RelpConfig;
import com.teragrep.aer_02.config.SyslogConfig;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

final class EventDataConsumer implements AutoCloseable {

    // Note: Checkpointing is handled automatically.
    private final Output output;
    private final String realHostName;
    private final SyslogConfig syslogConfig;
    private final MetricRegistry metricRegistry;
    private final JmxReporter jmxReporter;
    private final Slf4jReporter slf4jReporter;

    EventDataConsumer(Sourceable configSource, int prometheusPort) {
        this(configSource, new MetricRegistry(), prometheusPort);
    }

    EventDataConsumer(Sourceable configSource, MetricRegistry metricRegistry, int prometheusPort) {
        this(
                configSource,
                new DefaultOutput("defaultOutput", new RelpConfig(configSource), metricRegistry),
                metricRegistry,
                prometheusPort
        );
    }

    EventDataConsumer(Sourceable configSource, Output output, MetricRegistry metricRegistry, int prometheusPort) {
        this.metricRegistry = metricRegistry;
        this.output = output;
        this.realHostName = getRealHostName();
        this.syslogConfig = new SyslogConfig(configSource);

        this.jmxReporter = JmxReporter.forRegistry(metricRegistry).build();
        this.slf4jReporter = Slf4jReporter
                .forRegistry(metricRegistry)
                .outputTo(LoggerFactory.getLogger(EventDataConsumer.class))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        startMetrics();
    }

    private String getRealHostName() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            hostname = "localhost";
        }
        return hostname;
    }

    private void startMetrics() {
        this.jmxReporter.start();
        this.slf4jReporter.start(1, TimeUnit.MINUTES);

        // prometheus-exporter
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(metricRegistry));
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

        String eventUuid = null; //TODO: Message id not available?

        // FIXME proper handling of non-provided uuids
        if (eventUuid == null) {
            eventUuid = "aer_01=" + UUID.randomUUID();
        }

        SDElement sdId = new SDElement("event_id@48577")
                .addSDParam("hostname", realHostName)
                .addSDParam("uuid", eventUuid)
                .addSDParam("unixtime", Instant.now().toString())
                .addSDParam("id_source", "source");

        SDElement sdPartition = new SDElement("aer_01_partition@48577")
                .addSDParam(
                        "fully_qualified_namespace",
                        String.valueOf(partitionContext.getOrDefault("FullyQualifiedNamespace", ""))
                )
                .addSDParam("eventhub_name", String.valueOf(partitionContext.getOrDefault("EventHubName", "")))
                .addSDParam("partition_id", String.valueOf(partitionContext.getOrDefault("PartitionId", "")))
                .addSDParam("consumer_group", String.valueOf(partitionContext.getOrDefault("ConsumerGroup", "")));

        String partitionKey = String.valueOf(systemProps.getOrDefault("PartitionKey", ""));

        // TODO: Correlation id not available?
        // String correlationId = props.get("correlationId").toString();
        SDElement sdEvent = new SDElement("aer_01_event@48577")
                .addSDParam("offset", offset == null ? "" : offset)
                .addSDParam("enqueued_time", enqueuedTime == null ? "" : enqueuedTime.toString())
                .addSDParam("partition_key", partitionKey == null ? "" : partitionKey);
        //.addSDParam("correlation_id", correlationId == null ? "" : correlationId);
        props.forEach((key, value) -> sdEvent.addSDParam("property_" + key, value.toString()));

        SyslogMessage syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withTimestamp(enqueuedTime.toInstant())
                .withHostname(syslogConfig.hostname)
                .withAppName(syslogConfig.appName)
                .withSDElement(sdId)
                .withSDElement(sdPartition)
                .withSDElement(sdEvent)
                //.withSDElement(sdCorId)
                .withMsgId(String.valueOf(systemProps.getOrDefault("SequenceNumber", "0")))
                .withMsg(eventData);

        output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws Exception {
        output.close();
        slf4jReporter.close();
        jmxReporter.close();
    }
}
