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
package com.teragrep.aer_02.fakes;

import com.teragrep.aer_02.config.source.Sourceable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class SourceableFake implements Sourceable {

    private final Map<String, String> map;

    public SourceableFake() {
        this(new HashMap<>());
    }

    public SourceableFake(final Map<String, String> map) {
        this.map = map;
    }

    @Override
    public String source(final String name, final String defaultValue) {
        map.putIfAbsent("relp.tls.mode", "none");
        map.putIfAbsent("plugins.config.path", "");
        map.putIfAbsent("relp.connection.timeout", "2500");
        map.putIfAbsent("relp.transaction.read.timeout", "1500");
        map.putIfAbsent("relp.transaction.write.timeout", "1500");
        map.putIfAbsent("relp.connection.retry.interval", "500");
        map.putIfAbsent("relp.connection.port", "601");
        map.putIfAbsent("relp.connection.address", "localhost");
        map.putIfAbsent("relp.rebind.request.amount", "100000");
        map.putIfAbsent("relp.rebind.enabled", "true");
        map.putIfAbsent("relp.max.idle.duration", Duration.ofMillis(150000L).toString());
        map.putIfAbsent("relp.max.idle.enabled", "false");
        map.putIfAbsent("relp.connection.keepalive", "true");
        map.putIfAbsent("syslog.appname", "aer-02");
        map.putIfAbsent("syslog.hostname", "localhost.localdomain");
        return map.getOrDefault(name, defaultValue);
    }
}
