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
package com.teragrep.aer_02.records;

import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.akv_01.event.ParsedEventFactory;
import com.teragrep.akv_01.event.UnparsedEventImpl;
import com.teragrep.akv_01.event.metadata.offset.EventOffsetStub;
import com.teragrep.akv_01.event.metadata.partitionContext.EventPartitionContextStub;
import com.teragrep.akv_01.event.metadata.properties.EventPropertiesStub;
import com.teragrep.akv_01.event.metadata.systemProperties.EventSystemPropertiesStub;
import com.teragrep.akv_01.event.metadata.time.EnqueuedTimeStub;
import jakarta.json.Json;
import jakarta.json.JsonStructure;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventRecordsTest {

    @Test
    void testJsonArrayCase() {
        final JsonStructure records = Json.createArrayBuilder().add(0, 1).add(1, 2).build();
        final ParsedEvent parsedEvent = new ParsedEventFactory(
                new UnparsedEventImpl(
                        records.toString(),
                        new EventPartitionContextStub(),
                        new EventPropertiesStub(),
                        new EventSystemPropertiesStub(),
                        new EnqueuedTimeStub(),
                        new EventOffsetStub()
                )
        ).parsedEvent();
        EventRecords jr = new EventRecords(parsedEvent);
        Assertions.assertEquals(1, jr.records().size());
        Assertions.assertEquals(records.toString(), jr.records().get(0).asString());
    }

    @Test
    void testEmptyJsonObjectCase() {
        final JsonStructure records = Json.createObjectBuilder().build();
        final ParsedEvent parsedEvent = new ParsedEventFactory(
                new UnparsedEventImpl(
                        records.toString(),
                        new EventPartitionContextStub(),
                        new EventPropertiesStub(),
                        new EventSystemPropertiesStub(),
                        new EnqueuedTimeStub(),
                        new EventOffsetStub()
                )
        ).parsedEvent();
        EventRecords jr = new EventRecords(parsedEvent);
        Assertions.assertEquals(1, jr.records().size());
        Assertions.assertEquals(records.toString(), jr.records().get(0).asString());
    }

    @Test
    void testOtherJsonObjectCase() {
        final JsonStructure records = Json.createObjectBuilder().add("k1", "v1").add("k2", "v2").build();
        final ParsedEvent parsedEvent = new ParsedEventFactory(
                new UnparsedEventImpl(
                        records.toString(),
                        new EventPartitionContextStub(),
                        new EventPropertiesStub(),
                        new EventSystemPropertiesStub(),
                        new EnqueuedTimeStub(),
                        new EventOffsetStub()
                )
        ).parsedEvent();
        EventRecords jr = new EventRecords(parsedEvent);
        Assertions.assertEquals(1, jr.records().size());
        Assertions.assertEquals(records.toString(), jr.records().get(0).asString());
    }

    @Test
    void testRecordsAsObjectsCase() {
        final JsonStructure records = Json
                .createObjectBuilder()
                .add("records", Json.createArrayBuilder().add(Json.createObjectBuilder().add("a", "b")).add(Json.createObjectBuilder().add("c", "d"))).build();
        final ParsedEvent parsedEvent = new ParsedEventFactory(
                new UnparsedEventImpl(
                        records.toString(),
                        new EventPartitionContextStub(),
                        new EventPropertiesStub(),
                        new EventSystemPropertiesStub(),
                        new EnqueuedTimeStub(),
                        new EventOffsetStub()
                )
        ).parsedEvent();
        EventRecords jr = new EventRecords(parsedEvent);
        Assertions.assertEquals(2, jr.records().size());
        Assertions.assertEquals("{\"a\":\"b\"}", jr.records().get(0).asString());
        Assertions.assertEquals("{\"c\":\"d\"}", jr.records().get(1).asString());
    }

    @Test
    void testRecordsAsStringsAndNumbersCase() {
        final JsonStructure records = Json
                .createObjectBuilder()
                .add("records", Json.createArrayBuilder().add("abc").add(123).build())
                .build();
        final ParsedEvent parsedEvent = new ParsedEventFactory(
                new UnparsedEventImpl(
                        records.toString(),
                        new EventPartitionContextStub(),
                        new EventPropertiesStub(),
                        new EventSystemPropertiesStub(),
                        new EnqueuedTimeStub(),
                        new EventOffsetStub()
                )
        ).parsedEvent();
        EventRecords jr = new EventRecords(parsedEvent);
        Assertions.assertEquals(1, jr.records().size());
        Assertions.assertEquals(records.toString(), jr.records().get(0).asString());
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(EventRecords.class).verify();
    }
}
