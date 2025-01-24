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

public final class JsonResourceId {

    private final String message;

    public JsonResourceId(final String message) {
        this.message = message;
    }

    public String resourceId() throws JsonException {
        final String resourceIdKey = "resourceId";
        final JsonStructure mainStructure;
        try (final JsonReader reader = Json.createReader(new StringReader(message))) {
            mainStructure = reader.read();
        }
        catch (JsonParsingException e) {
            // Could not parse JSON, return empty id
            throw new JsonException("Could not parse message into JSON", e);
        }

        if (mainStructure == null || !mainStructure.getValueType().equals(JsonValue.ValueType.OBJECT)) {
            // No main structure or it wasn't a JSONObject, return empty id
            throw new JsonException("Missing main structure, expected JSON object");
        }

        final JsonObject mainObject = mainStructure.asJsonObject();

        if (!mainObject.containsKey(resourceIdKey)) {
            // No "resourceId" key present in JSONObject, return empty id
            throw new JsonException("Missing key <" + resourceIdKey + "> in main structure");
        }

        if (!mainObject.get(resourceIdKey).getValueType().equals(JsonValue.ValueType.STRING)) {
            // resourceId was not a string, return empty id
            throw new JsonException("Key <" + resourceIdKey + "> was not of the expected type String");
        }

        return mainObject.getJsonString(resourceIdKey).getString();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JsonResourceId that = (JsonResourceId) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(message);
    }
}
