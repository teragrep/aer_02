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
package com.teragrep.aer_02.plugin;

import com.microsoft.azure.functions.ExecutionContext;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.aer_02.fakes.ExecutionContextFake;
import com.teragrep.aer_02.fakes.SourceableFake;
import com.teragrep.akv_01.plugin.PluginFactoryConfig;
import com.teragrep.akv_01.plugin.PluginFactoryConfigImpl;
import com.teragrep.rlo_14.SyslogMessage;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public final class EventMessageToPluginTest {

    @Test
    void testNonMatchingResourceId() {
        final String unrefinedMsg = "{\"resourceId\":\"default\"}";
        final Sourceable source = new SourceableFake();
        final Map<String, PluginFactoryConfig> configs = new HashMap<>();
        configs.put("123", new PluginFactoryConfigImpl("com.teragrep.aer_02.fakes.ThrowingPluginFactory", "path"));
        final String defaultClass = "com.teragrep.aer_02.plugin.DefaultPluginFactory";
        final String hostname = "localhost";
        final ExecutionContext context = new ExecutionContextFake();

        final EventMessageToPlugin eventMessageToPlugin = new EventMessageToPlugin(
                unrefinedMsg,
                source,
                configs,
                defaultClass,
                hostname,
                context
        );

        final SyslogMessage msg = eventMessageToPlugin
                .toPlugin()
                .syslogMessage("event", new HashMap<>(), ZonedDateTime.now(), "", new HashMap<>(), new HashMap<>());
        Assertions.assertEquals("event", msg.getMsg());
        Assertions.assertEquals("localhost.localdomain", msg.getHostname());
    }

    @Test
    void testMatchingResourceId() {
        final String unrefinedMsg = "{\"resourceId\":\"123\"}";
        final Sourceable source = new SourceableFake();
        final Map<String, PluginFactoryConfig> configs = new HashMap<>();
        configs.put("123", new PluginFactoryConfigImpl("com.teragrep.aer_02.fakes.ThrowingPluginFactory", "path"));
        final String defaultClass = "com.teragrep.aer_02.plugin.DefaultPluginFactory";
        final String hostname = "localhost";
        final ExecutionContext context = new ExecutionContextFake();

        final EventMessageToPlugin eventMessageToPlugin = new EventMessageToPlugin(
                unrefinedMsg,
                source,
                configs,
                defaultClass,
                hostname,
                context
        );

        final RuntimeException rte = Assertions.assertThrows(RuntimeException.class, eventMessageToPlugin::toPlugin);
        Assertions.assertEquals("This is a test", rte.getMessage());
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(EventMessageToPlugin.class).verify();
    }
}
