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

import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.akv_01.time.EnqueuedTime;
import jakarta.json.JsonStructure;

import java.util.HashMap;
import java.util.Map;

public final class ParsedEventWithException implements ParsedEvent {

    private final ParsedEvent parsedEvent;
    private final Throwable exception;

    public ParsedEventWithException(final ParsedEvent parsedEvent, final Throwable exception) {
        this.parsedEvent = parsedEvent;
        this.exception = exception;
    }

    @Override
    public JsonStructure asJsonStructure() {
        return parsedEvent.asJsonStructure();
    }

    @Override
    public boolean isJsonStructure() {
        return parsedEvent.isJsonStructure();
    }

    @Override
    public String asString() {
        return parsedEvent.asString();
    }

    @Override
    public String resourceId() {
        return parsedEvent.resourceId();
    }

    @Override
    public Map<String, Object> partitionContext() {
        return parsedEvent.partitionContext();
    }

    @Override
    public Map<String, Object> properties() {
        final Map<String, Object> props = new HashMap<>(parsedEvent.properties());
        props.put("aer-02-exception", exception);
        return props;
    }

    @Override
    public Map<String, Object> systemProperties() {
        return parsedEvent.systemProperties();
    }

    @Override
    public EnqueuedTime enqueuedTime() {
        return parsedEvent.enqueuedTime();
    }

    @Override
    public String offset() {
        return parsedEvent.offset();
    }
}
