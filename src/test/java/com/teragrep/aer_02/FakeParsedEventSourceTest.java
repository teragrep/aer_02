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

import com.teragrep.akv_01.event.ParsedEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class FakeParsedEventSourceTest {

    @Test
    public void testFakeParsedEventSource() {
        FakeParsedEventSource fakeParsedEventSource = new FakeParsedEventSource();

        int numberOfEvents = 10;

        List<ParsedEvent> parsedEvents = fakeParsedEventSource.parsedEvents(numberOfEvents, 1);

        Assertions.assertEquals(numberOfEvents, parsedEvents.size());

        DateTimeFormatter enqueuedTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime startEnqueuedTime = LocalDateTime
                .parse("2010-01-01T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        int loops = 0;
        for (int eventNumber = 0; eventNumber < numberOfEvents; eventNumber++) {
            Assertions
                    .assertEquals(startEnqueuedTime.plusSeconds(eventNumber).format(enqueuedTimeFormatter), parsedEvents.get(eventNumber).enqueuedTimeUtc().toString());
            Assertions.assertEquals("x_event_" + eventNumber, parsedEvents.get(eventNumber).payload());
            Assertions
                    .assertEquals(String.valueOf(eventNumber), parsedEvents.get(eventNumber).systemProperties().asMap().get("SequenceNumber"));
            Assertions.assertTrue(parsedEvents.get(eventNumber).properties().asMap().isEmpty());
            Assertions.assertEquals("test1", parsedEvents.get(eventNumber).partitionCtx().asMap().get("EventHubName"));
            Assertions
                    .assertEquals("eventhub.123", parsedEvents.get(eventNumber).partitionCtx().asMap().get("FullyQualifiedNamespace"));
            Assertions.assertEquals("0", parsedEvents.get(eventNumber).partitionCtx().asMap().get("PartitionId"));
            Assertions
                    .assertEquals("$Default", parsedEvents.get(eventNumber).partitionCtx().asMap().get("ConsumerGroup"));
            Assertions.assertFalse(parsedEvents.get(eventNumber).isJsonStructure());
            loops++;
        }
        Assertions.assertEquals(numberOfEvents, loops);
    }

}
