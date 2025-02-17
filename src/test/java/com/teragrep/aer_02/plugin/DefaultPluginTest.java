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

import com.teragrep.akv_01.event.ParsedEventFactory;
import com.teragrep.akv_01.event.UnparsedEventImpl;
import com.teragrep.akv_01.event.metadata.offset.EventOffsetImpl;
import com.teragrep.akv_01.event.metadata.partitionContext.EventPartitionContextImpl;
import com.teragrep.akv_01.event.metadata.properties.EventPropertiesImpl;
import com.teragrep.akv_01.event.metadata.systemProperties.EventSystemPropertiesImpl;
import com.teragrep.akv_01.event.metadata.time.EnqueuedTimeImpl;
import com.teragrep.rlo_14.SyslogMessage;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DefaultPluginTest {

    @Test
    void testDefaultPluginCreateSyslogMessage() {
        final Map<String, Object> partitionContext = new HashMap<>();
        partitionContext.put("FullyQualifiedNamespace", "eventhub.123");
        partitionContext.put("EventHubName", "test1");
        partitionContext.put("ConsumerGroup", "$Default");
        partitionContext.put("PartitionId", "0");
        final Map<String, Object> props = new HashMap<>();
        final Map<String, Object> systemProps = new HashMap<>();
        systemProps.put("SequenceNumber", "1");
        final String enqueuedTime = "2025-01-01T01:01:01";

        final DefaultPlugin defaultPlugin = new DefaultPlugin("realHostname", "syslogHostname", "syslogAppname");
        final List<SyslogMessage> msg = defaultPlugin
                .syslogMessage(
                        new ParsedEventFactory(
                                new UnparsedEventImpl("event", new EventPartitionContextImpl(partitionContext), new EventPropertiesImpl(props), new EventSystemPropertiesImpl(systemProps), new EnqueuedTimeImpl(enqueuedTime), new EventOffsetImpl("0"))
                        ).parsedEvent()
                );

        Assertions.assertEquals("event", msg.get(0).getMsg());
        // aer_02@48577; aer_02_event@48577; aer_02_partition@48577; event_id@48577
        Assertions.assertEquals(4, msg.get(0).getSDElements().size());
        Assertions.assertEquals("syslogHostname", msg.get(0).getHostname());
        Assertions.assertEquals("syslogAppname", msg.get(0).getAppName());
        Assertions
                .assertEquals(ZonedDateTime.parse(enqueuedTime + "Z").toInstant().toString(), msg.get(0).getTimestamp());
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(DefaultPlugin.class).verify();
    }
}
