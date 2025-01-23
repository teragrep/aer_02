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

import com.codahale.metrics.MetricRegistry;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.teragrep.aer_02.config.RelpConnectionConfig;
import com.teragrep.aer_02.config.source.EnvironmentSource;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.aer_02.json.JsonRecords;
import com.teragrep.aer_02.metrics.JmxReport;
import com.teragrep.aer_02.metrics.PrometheusReport;
import com.teragrep.aer_02.metrics.Report;
import com.teragrep.aer_02.metrics.Slf4jReport;
import com.teragrep.aer_02.tls.AzureSSLContextSupplier;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SyslogBridge {

    public SyslogBridge() {

    }

    @FunctionName("metrics")
    public HttpResponseMessage metrics(
            @HttpTrigger(
                    name = "req",
                    methods = {
                            HttpMethod.GET, HttpMethod.POST
                    },
                    authLevel = AuthorizationLevel.FUNCTION
            ) HttpRequestMessage<Optional<String>> request,
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
    public void eventHubTriggerToSyslog(
            @EventHubTrigger(
                    name = "event",
                    /* Name of the EVENT HUB, not the app setting. Wrapping value in %'s makes it an environment variable.
                     * This makes it configurable in app settings. */
                    eventHubName = "%EventHubName%",
                    // Name of the APPLICATION SETTING
                    connection = "EventHubConnectionString",
                    dataType = "string",
                    cardinality = Cardinality.MANY
            ) String[] events,
            @BindingName("PartitionContext") Map<String, Object> partitionContext,
            @BindingName("PropertiesArray") Map<String, Object>[] propertiesArray,
            @BindingName("SystemPropertiesArray") Map<String, Object>[] systemPropertiesArray,
            @BindingName("EnqueuedTimeUtcArray") List<Object> enqueuedTimeUtcArray,
            @BindingName("OffsetArray") List<String> offsetArray,
            ExecutionContext context
    ) {
        try {
            context.getLogger().fine("eventHubTriggerToSyslog triggered");
            context.getLogger().fine("Got events: " + events.length);

            final Sourceable configSource = new EnvironmentSource();
            final String hostname = new Hostname("localhost").hostname();

            final MetricRegistry metricRegistry = new MetricRegistry();
            ;

            context.getLogger().info("initializing at " + this);

            final Report report = new JmxReport(
                    new Slf4jReport(new PrometheusReport(new DropwizardExports(metricRegistry)), metricRegistry),
                    metricRegistry
            );
            report.start();

            DefaultOutput defaultOutput;
            if (configSource.source("relp.tls.mode", "none").equals("keyVault")) {
                context.getLogger().info("connection tls enabled");

                defaultOutput = new DefaultOutput(
                        context.getLogger(),
                        "defaultOutput",
                        new RelpConnectionConfig(configSource),
                        metricRegistry,
                        new AzureSSLContextSupplier()
                );
            }
            else {
                context.getLogger().info("connection tls disabled");
                defaultOutput = new DefaultOutput(
                        context.getLogger(),
                        "defaultOutput",
                        new RelpConnectionConfig(configSource),
                        metricRegistry
                );
            }

            context.getLogger().info("initialized at " + this);

            EventDataConsumer consumer = new EventDataConsumer(configSource, defaultOutput, hostname, metricRegistry);

            for (int index = 0; index < events.length; index++) {
                if (events[index] != null) {
                    final ZonedDateTime et = ZonedDateTime.parse(enqueuedTimeUtcArray.get(index) + "Z"); // needed as the UTC time presented does not have a TZ
                    context.getLogger().fine("Accepting event: " + events[index]);
                    final String[] records = new JsonRecords(events[index]).records();
                    for (final String record : records) {
                        consumer
                                .accept(record, partitionContext, et, offsetArray.get(index), propertiesArray[index], systemPropertiesArray[index]);
                    }
                }
                else {
                    context.getLogger().warning("eventHubTriggerToSyslog event data is null");
                }
            }

            // close connections to prevent resource leak
            defaultOutput.close();
        }
        catch (Throwable t) {
            context.getLogger().severe("exiting because caught Throwable: " + t);
            System.exit(1);
        }
    }
}
