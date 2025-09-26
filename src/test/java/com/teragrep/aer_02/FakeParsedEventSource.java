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

import com.teragrep.aer_02.fakes.PartitionContextFake;
import com.teragrep.aer_02.fakes.SystemPropsFake;
import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.akv_01.event.ParsedEventFactory;
import com.teragrep.akv_01.event.UnparsedEventImpl;
import com.teragrep.akv_01.event.metadata.offset.EventOffsetImpl;
import com.teragrep.akv_01.event.metadata.partitionContext.EventPartitionContextImpl;
import com.teragrep.akv_01.event.metadata.properties.EventPropertiesImpl;
import com.teragrep.akv_01.event.metadata.systemProperties.EventSystemProperties;
import com.teragrep.akv_01.event.metadata.systemProperties.EventSystemPropertiesImpl;
import com.teragrep.akv_01.event.metadata.time.EnqueuedTime;
import com.teragrep.akv_01.event.metadata.time.EnqueuedTimeImpl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class FakeParsedEventSource {

    private final PartitionContextFake partitionContextFake;

    public FakeParsedEventSource() {
        this(new PartitionContextFake("eventhub.123", "test1", "$Default", "0"));
    }

    public FakeParsedEventSource(final PartitionContextFake partitionContextFake) {
        this.partitionContextFake = partitionContextFake;
    }

    public List<ParsedEvent> parsedEvents(int numberOfEvents, int eventSizeIncrement) {
        DateTimeFormatter enqueuedTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime startEnqueuedTime = LocalDateTime
                .parse("2010-01-01T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        List<ParsedEvent> parsedEvents = new ArrayList<>();
        for (int i = 0; i < numberOfEvents; i++) {
            String payload = "x".repeat(eventSizeIncrement) + "_event_" + i;

            EnqueuedTime enqueuedTimeUtc = new EnqueuedTimeImpl(
                    startEnqueuedTime.plusSeconds(i).format(enqueuedTimeFormatter)
            );

            Map<String, Object> propertiesMap = Collections.emptyMap();

            EventSystemProperties eventSystemProperties = new EventSystemPropertiesImpl(
                    new SystemPropsFake(String.valueOf(i)).asMap()
            );
            parsedEvents
                    .add(
                            new ParsedEventFactory(
                                    new UnparsedEventImpl(
                                            payload,
                                            new EventPartitionContextImpl(partitionContextFake.asMap()),
                                            new EventPropertiesImpl(propertiesMap),
                                            eventSystemProperties,
                                            enqueuedTimeUtc,
                                            new EventOffsetImpl(String.valueOf(i))
                                    )
                            ).parsedEvent()
                    );
        }
        return parsedEvents;
    }
}
