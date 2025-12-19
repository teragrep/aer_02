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
import com.microsoft.azure.functions.annotation.*;
import com.teragrep.aer_02.plugin.LazyPluginMapInstance;
import com.teragrep.aer_02.plugin.WrappedPluginFactoryWithConfig;
import com.teragrep.akv_01.event.ParsedEventListFactory;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class SyslogBridge {

    public SyslogBridge() {

    }

    @FunctionName("metrics")
    public HttpResponseMessage metrics(
            @HttpTrigger(
                    name = "req",
                    methods = {
                            HttpMethod.GET, HttpMethod.POST
                    },
                    authLevel = AuthorizationLevel.ANONYMOUS
            ) final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        context.getLogger().fine("Metrics HTTP trigger was triggered");
        String contentType = TextFormat.chooseContentType(request.getHeaders().get("Accept"));

        String body;
        try (Writer writer = new StringWriter()) {
            TextFormat.writeFormat(contentType, writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            body = writer.toString();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return request.createResponseBuilder(HttpStatus.OK).body(body).header("Accept", contentType).build();
    }

    @FunctionName("eventHubTriggerToSyslog")
    public synchronized void eventHubTriggerToSyslog(
            @EventHubTrigger(
                    name = "event",
                    /* Name of the EVENT HUB, not the app setting. Wrapping value in %'s makes it an environment variable.
                     * This makes it configurable in app settings. */
                    eventHubName = "%EventHubName%",
                    // Name of the APPLICATION SETTING
                    connection = "EventHubConnectionString",
                    dataType = "string",
                    cardinality = Cardinality.MANY
            ) final String[] events,
            @BindingName("PartitionContext") final Map<String, Object> partitionContext,
            @BindingName("PropertiesArray") final Map<String, Object>[] propertiesArray,
            @BindingName("SystemPropertiesArray") final Map<String, Object>[] systemPropertiesArray,
            @BindingName("EnqueuedTimeUtcArray") final List<Object> enqueuedTimeUtcArray,
            @BindingName("OffsetArray") final List<String> offsetArray,
            final ExecutionContext context
    ) {
        try {
            if (context.getLogger().isLoggable(Level.FINE)) {
                long sekvenssiCount = 0;
                for (final String event : events) {
                    if (event.matches(".*sekvenssi_daemonset_.*")) {
                        sekvenssiCount++;
                        if (context.getLogger().isLoggable(Level.FINER)) {
                            context.getLogger().finer("sekvenssi_daemonset_ event <[" + event + "]>");
                        }
                    }
                }

                context.getLogger().fine("sekvenssi_daemonset_ sekvenssiCount <" + sekvenssiCount + ">");
            }

            if (context.getLogger().isLoggable(Level.FINE)) {
                context.getLogger().fine("eventHubTriggerToSyslog triggered");
                context.getLogger().fine("Got events: <[" + events.length + "]>");
            }
            final LazyInstance lazyInstance = LazyInstance.lazySingletonInstance();
            final LazyPluginMapInstance lazyPluginMapInstance = LazyPluginMapInstance.lazySingletonInstance();

            final DefaultOutput defaultOutput = lazyInstance.defaultOutput();
            final Map<String, WrappedPluginFactoryWithConfig> pluginFactories = lazyPluginMapInstance.pluginFactories();
            final WrappedPluginFactoryWithConfig defaultPluginFactory = lazyPluginMapInstance.defaultPluginFactory();
            final WrappedPluginFactoryWithConfig exceptionPluginFactory = lazyPluginMapInstance
                    .exceptionPluginFactory();

            final EventDataConsumer consumer = new EventDataConsumer(
                    defaultOutput,
                    pluginFactories,
                    defaultPluginFactory,
                    exceptionPluginFactory,
                    lazyInstance.metricRegistry()
            );
            consumer
                    .accept(
                            new ParsedEventListFactory(
                                    events,
                                    partitionContext,
                                    propertiesArray,
                                    systemPropertiesArray,
                                    enqueuedTimeUtcArray,
                                    offsetArray
                            ).asList()
                    );

        }
        catch (Throwable t) {
            if (context.getLogger().isLoggable(Level.SEVERE)) {
                context
                        .getLogger()
                        .log(
                                Level.SEVERE,
                                "Exiting because unexpected throwable was caught with message <" + t.getMessage() + ">",
                                t
                        );
            }
            System.exit(1);
            throw t;
        }
    }
}
