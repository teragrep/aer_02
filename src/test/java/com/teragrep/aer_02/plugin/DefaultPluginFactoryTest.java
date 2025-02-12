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

import com.teragrep.aer_02.SyslogBridge;
import com.teragrep.akv_01.event.EventImpl;
import com.teragrep.akv_01.plugin.Plugin;
import com.teragrep.akv_01.plugin.PluginFactory;
import com.teragrep.akv_01.plugin.PluginFactoryInitialization;
import com.teragrep.rlo_14.SyslogMessage;
import jakarta.json.Json;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.HashMap;
import java.util.List;

public final class DefaultPluginFactoryTest {

    @Test
    void testPluginFactoryCreateObject() {
        final DefaultPluginFactory factory = new DefaultPluginFactory();
        final Plugin plugin = Assertions
                .assertDoesNotThrow(() -> factory.plugin(Json.createObjectBuilder().add("realHostname", "hostname").add("syslogHostname", "hostname").add("syslogAppname", "appname").build().toString()));
        final LocalDateTime now = LocalDateTime.now(ZoneId.of("Z"));
        final List<SyslogMessage> msg = Assertions
                .assertDoesNotThrow(
                        () -> plugin
                                .syslogMessage(
                                        new EventImpl("msg", new HashMap<>(), new HashMap<>(), new HashMap<>(), now, "").parsedEvent()
                                )
                );
        Assertions.assertEquals(DefaultPlugin.class, plugin.getClass());
        Assertions.assertEquals("msg", msg.get(0).getMsg());
        Assertions
                .assertEquals(Instant.ofEpochMilli(now.toInstant(ZoneOffset.UTC).toEpochMilli()).toString(), msg.get(0).getTimestamp());
    }

    @Test
    void testPluginFactoryInitialization() {
        final PluginFactoryInitialization pluginFactoryInitialization = new PluginFactoryInitialization(
                "com.teragrep.aer_02.plugin.DefaultPluginFactory"
        );
        final PluginFactory pluginFactory = Assertions.assertDoesNotThrow(pluginFactoryInitialization::pluginFactory);
        Assertions.assertEquals(DefaultPluginFactory.class, pluginFactory.getClass());
        final Plugin plugin = Assertions
                .assertDoesNotThrow(() -> pluginFactory.plugin(Json.createObjectBuilder().add("realHostname", "hostname").add("syslogHostname", "hostname").add("syslogAppname", "appname").build().toString()));
        Assertions.assertEquals(DefaultPlugin.class, plugin.getClass());
    }

    @Test
    void testNonExistentClass() {
        final PluginFactoryInitialization pluginFactoryInitialization = new PluginFactoryInitialization(
                "notARealClassName"
        );

        Assertions.assertThrows(ClassNotFoundException.class, pluginFactoryInitialization::pluginFactory);
    }

    @Test
    void testIncompatibleClass() {
        final PluginFactoryInitialization pluginFactoryInitialization = new PluginFactoryInitialization(
                SyslogBridge.class.getName()
        );

        Assertions.assertThrows(ClassCastException.class, pluginFactoryInitialization::pluginFactory);
    }
}
