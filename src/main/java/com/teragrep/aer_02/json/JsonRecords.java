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
package com.teragrep.aer_02.json;

import jakarta.json.*;
import jakarta.json.stream.JsonParsingException;

import java.io.StringReader;
import java.util.Objects;

public final class JsonRecords {

    private final String event;

    public JsonRecords(final String event) {
        this.event = event;
    }

    /**
     * Expects <code>{"records":[{},{}, ..., {}]}</code> type JSON string.
     * 
     * @return individual records as an array or the original event.
     */
    public String[] records() {
        final String[] rv = new String[] {
                event
        };

        final JsonStructure mainStructure;
        try (final JsonReader reader = Json.createReader(new StringReader(event))) {
            mainStructure = reader.read();
        }
        catch (JsonParsingException e) {
            // pass event through as-is if JSON parsing fails
            return rv;
        }

        if (mainStructure == null || !mainStructure.getValueType().equals(JsonValue.ValueType.OBJECT)) {
            // pass event through as-is if top-level structure is not object or doesn't exist
            return rv;
        }

        final JsonValue recordsStructure = mainStructure.asJsonObject().get("records");

        if (recordsStructure != null && recordsStructure.getValueType().equals(JsonValue.ValueType.ARRAY)) {
            final JsonArray recordsArray = recordsStructure.asJsonArray();
            String[] records = new String[recordsArray.size()];

            for (int i = 0; i < recordsArray.size(); i++) {
                // Take string representation of inner value regardless of actual datatype
                records[i] = recordsArray.get(i).toString();
            }

            return records;
        }

        // pass event through as-is if "records" is not an array type or doesn't exist
        return rv;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JsonRecords that = (JsonRecords) o;
        return Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(event);
    }
}
