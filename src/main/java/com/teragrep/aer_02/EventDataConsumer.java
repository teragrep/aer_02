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
import com.teragrep.aer_02.config.SyslogConfig;
import com.teragrep.aer_02.config.source.EnvironmentSource;
import com.teragrep.aer_02.plugin.ResourceIdToPluginMap;
import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.SyslogMessage;
import com.teragrep.rlo_14.*;
import com.teragrep.akv_01.plugin.*;
import jakarta.json.Json;
import jakarta.json.JsonException;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.codahale.metrics.MetricRegistry.name;

final class EventDataConsumer {

    // Note: Checkpointing is handled automatically.
    private final Logger logger;
    private final Output output;
    private final Map<String, PluginFactoryConfig> pluginFactoryConfigs;
    private final MetricRegistry metricRegistry;
    private final String defaultPluginFactoryClassName;
    private final SyslogConfig syslogConfig;
    private final Hostname hostName;

    EventDataConsumer(
            final Logger logger,
            final Output output,
            final Map<String, PluginFactoryConfig> pluginFactoryConfigs,
            final String defaultPluginFactoryClassName,
            final MetricRegistry metricRegistry
    ) {
        this(
                logger,
                output,
                pluginFactoryConfigs,
                defaultPluginFactoryClassName,
                metricRegistry,
                new SyslogConfig(new EnvironmentSource()),
                new Hostname("localhost")
        );
    }

    EventDataConsumer(
            final Logger logger,
            final Output output,
            final Map<String, PluginFactoryConfig> pluginFactoryConfigs,
            final String defaultPluginFactoryClassName,
            final MetricRegistry metricRegistry,
            final SyslogConfig syslogConfig,
            final Hostname hostName
    ) {
        this.logger = logger;
        this.metricRegistry = metricRegistry;
        this.pluginFactoryConfigs = pluginFactoryConfigs;
        this.defaultPluginFactoryClassName = defaultPluginFactoryClassName;
        this.output = output;
        this.syslogConfig = syslogConfig;
        this.hostName = hostName;
    }

    public void accept(final List<ParsedEvent> parsedEvents) {
        for (final ParsedEvent parsedEvent : parsedEvents) {
            final Plugin plugin = pluginFor(parsedEvent);

            final List<SyslogMessage> syslogMessages = plugin.syslogMessage(parsedEvent);
            syslogMessages.forEach(this::sendToOutput);
        }
    }

    private Plugin pluginFor(final ParsedEvent parsedEvent) {
        // default plugin config
        PluginFactoryConfig pfc = new PluginFactoryConfigImpl(
                defaultPluginFactoryClassName,
                Json.createObjectBuilder().add("realHostname", hostName.hostname()).add("syslogHostname", syslogConfig.hostName()).add("syslogAppname", syslogConfig.appName()).build().toString()
        );

        if (!parsedEvent.isJsonStructure()) {
            // non-json event cannot have a resourceId, return default
            return newPlugin(pfc);
        }

        try {
            // Check if plugin config present for id
            final String resourceId = parsedEvent.resourceId();
            if (pluginFactoryConfigs.containsKey(resourceId)) {
                pfc = pluginFactoryConfigs.get(resourceId);
            }
        }
        catch (JsonException ignored) {
            // ignore exception, use default plugin
        }

        return newPlugin(pfc);
    }

    private Plugin newPlugin(final PluginFactoryConfig cfg) {
        try {
            return new PluginFactoryInitialization(cfg.pluginFactoryClassName())
                    .pluginFactory()
                    .plugin(cfg.configPath());
        }
        catch (
                ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException
                | IllegalAccessException e
        ) {
            logger.throwing(ResourceIdToPluginMap.class.getName(), "asUnmodifiableMap", e);
            throw new IllegalStateException(e);
        }
    }

    private void sendToOutput(final SyslogMessage syslogMessage) {
        final List<SDElement> partitionElements = syslogMessage
                .getSDElements()
                .stream()
                .filter(sdElement -> sdElement.getSdID().equals("aer_02_partition@48577"))
                .collect(Collectors.toList());
        if (partitionElements.isEmpty()) {
            throw new IllegalStateException("SDElement aer_02_partition@48577 not found");
        }

        final List<SDParam> partitionParams = partitionElements
                .get(0)
                .getSdParams()
                .stream()
                .filter(sdParam -> sdParam.getParamName().equals("partition_id"))
                .collect(Collectors.toList());
        if (partitionParams.isEmpty()) {
            throw new IllegalStateException("SDParam partition_id not found in SDElement aer_02_partition@48577");
        }

        final long timestampSecs = Instant.parse(syslogMessage.getTimestamp()).toEpochMilli() / 1000L;

        metricRegistry
                .gauge(name(EventDataConsumer.class, "latency-seconds", partitionParams.get(0).getParamValue()), () -> (Gauge<Long>) () -> Instant.now().getEpochSecond() - timestampSecs);

        output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
    }
}
