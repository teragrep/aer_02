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
package com.teragrep.aer_02.plugin;

import com.teragrep.aer_02.json.JsonRecords;
import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.akv_01.plugin.Plugin;
import com.teragrep.nlf_01.PropertiesJson;
import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;
import jakarta.json.JsonException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class DefaultPlugin implements Plugin {

    private final String realHostname;
    private final String syslogHostname;
    private final String syslogAppname;

    public DefaultPlugin(final String realHostname, final String syslogHostname, final String syslogAppname) {
        this.realHostname = realHostname;
        this.syslogHostname = syslogHostname;
        this.syslogAppname = syslogAppname;
    }

    @Override
    public List<SyslogMessage> syslogMessage(final ParsedEvent parsedEvent) {
        final List<SyslogMessage> syslogMessages = new ArrayList<>();
        ZonedDateTime time;
        boolean timeSet;
        try {
            time = parsedEvent.enqueuedTime().zonedDateTime();
            timeSet = true;
        }
        catch (DateTimeParseException ignored) {
            time = ZonedDateTime.now();
            timeSet = false;
        }

        final SDElement sdId = new SDElement("event_id@48577")
                .addSDParam("uuid", UUID.randomUUID().toString())
                .addSDParam("hostname", realHostname)
                .addSDParam("unixtime", Instant.now().toString())
                .addSDParam("id_source", "aer_02");

        final SDElement sdPartition = new SDElement("aer_02_partition@48577")
                .addSDParam(
                        "fully_qualified_namespace",
                        String.valueOf(parsedEvent.partitionContext().getOrDefault("FullyQualifiedNamespace", ""))
                )
                .addSDParam(
                        "eventhub_name", String.valueOf(parsedEvent.partitionContext().getOrDefault("EventHubName", ""))
                )
                .addSDParam("partition_id", String.valueOf(parsedEvent.partitionContext().getOrDefault("PartitionId", ""))).addSDParam("consumer_group", String.valueOf(parsedEvent.partitionContext().getOrDefault("ConsumerGroup", "")));

        final String partitionKey = String.valueOf(parsedEvent.systemProperties().getOrDefault("PartitionKey", ""));

        final SDElement sdEvent = new SDElement("aer_02_event@48577")
                .addSDParam("offset", parsedEvent.offset() == null ? "" : parsedEvent.offset())
                .addSDParam("enqueued_time", timeSet ? time.toInstant().toString() : "")
                .addSDParam("partition_key", partitionKey == null ? "" : partitionKey)
                .addSDParam("properties", new PropertiesJson(parsedEvent.properties()).toJsonObject().toString());

        final SDElement sdComponentInfo = new SDElement("aer_02@48577")
                .addSDParam("timestamp_source", timeSet ? "timeEnqueued" : "generated");

        List<String> records = new ArrayList<>();
        if (parsedEvent.isJsonStructure()) {
            JsonRecords jsonRecords = new JsonRecords(parsedEvent.asJsonStructure());
            try {
                records = jsonRecords.records();
            }
            catch (JsonException ignored) {
                records.add(parsedEvent.asString());
            }
        }
        else {
            records.add(parsedEvent.asString());
        }

        for (final String record : records) {
            syslogMessages
                    .add(new SyslogMessage().withSeverity(Severity.INFORMATIONAL).withFacility(Facility.LOCAL0).withTimestamp(timeSet ? time.toInstant().toEpochMilli() : Instant.now().toEpochMilli()).withHostname(syslogHostname).withAppName(syslogAppname).withSDElement(sdId).withSDElement(sdPartition).withSDElement(sdEvent).withSDElement(sdComponentInfo).withMsgId(String.valueOf(parsedEvent.systemProperties().getOrDefault("SequenceNumber", "0"))).withMsg(record));
        }

        return syslogMessages;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPlugin that = (DefaultPlugin) o;
        return Objects.equals(realHostname, that.realHostname) && Objects.equals(syslogHostname, that.syslogHostname)
                && Objects.equals(syslogAppname, that.syslogAppname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(realHostname, syslogHostname, syslogAppname);
    }
}
