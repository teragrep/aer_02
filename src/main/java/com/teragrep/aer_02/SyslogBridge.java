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
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.teragrep.aer_02.config.MetricsConfig;
import com.teragrep.aer_02.config.source.EnvironmentSource;
import com.teragrep.aer_02.config.source.PropertySource;
import com.teragrep.aer_02.config.source.Sourceable;

public class SyslogBridge {

    @FunctionName("eventHubTriggerToSyslog")
    public void eventHubTriggerToSyslog(
            @EventHubTrigger(
                    name = "event",
                    eventHubName = "EventHubName",
                    connection = "EventHubConnectionString",
                    dataType = "string",
                    cardinality = Cardinality.MANY
            ) EventData[] events,
            final ExecutionContext context,
            @BindingName("PartitionContext") PartitionContext partitionContext
    ) {
        final Sourceable configSource = configSource();
        final int prometheusPort = new MetricsConfig(configSource).prometheusPort;

        EventDataConsumer consumer = new EventDataConsumer(configSource, prometheusPort);

        for (EventData eventData : events) {
            consumer.accept(eventData, partitionContext);
        }
    }

    private Sourceable configSource() {
        String type = System.getProperty("config.source", "properties");

        Sourceable rv;
        if ("properties".equals(type)) {
            rv = new PropertySource();
        }
        else if ("environment".equals(type)) {
            rv = new EnvironmentSource();
        }
        else {
            throw new IllegalArgumentException("config.source not within supported types: [properties, environment]");
        }

        return rv;
    }
}
