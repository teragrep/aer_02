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

import com.teragrep.aer_02.config.SyslogConfig;
import com.teragrep.aer_02.fakes.ThrowingPluginFactory;
import com.teragrep.akv_01.plugin.PluginFactoryConfig;
import com.teragrep.akv_01.plugin.PluginFactoryConfigImpl;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class MappedPluginFactoriesTest {

    @Test
    void testWithOnePluginFactory() {
        final Map<String, PluginFactoryConfig> configs = new HashMap<>();
        configs.put("123", new PluginFactoryConfigImpl("com.teragrep.aer_02.fakes.ThrowingPluginFactory", "path"));

        final MappedPluginFactories mappedPluginFactories = new MappedPluginFactories(
                configs,
                "com.teragrep.aer_02.plugin.DefaultPluginFactory",
                "host",
                new SyslogConfig("app", "host"),
                Logger.getAnonymousLogger()
        );

        Map<String, WrappedPluginFactoryWithConfig> plugins = mappedPluginFactories.asUnmodifiableMap();
        Assertions.assertEquals(1, plugins.size());
        Assertions.assertTrue(plugins.containsKey("123"));
        Assertions
                .assertEquals(ThrowingPluginFactory.class.getName(), plugins.get("123").pluginFactory().getClass().getName());
        Assertions
                .assertEquals(
                        DefaultPluginFactory.class.getName(),
                        mappedPluginFactories.defaultPluginFactoryWithConfig().pluginFactory().getClass().getName()
                );
    }

    @Test
    void testWithNoPluginFactories() {
        final Map<String, PluginFactoryConfig> configs = new HashMap<>();
        final MappedPluginFactories mappedPluginFactories = new MappedPluginFactories(
                configs,
                "com.teragrep.aer_02.plugin.DefaultPluginFactory",
                "host",
                new SyslogConfig("app", "host"),
                Logger.getAnonymousLogger()
        );

        Map<String, WrappedPluginFactoryWithConfig> plugins = mappedPluginFactories.asUnmodifiableMap();
        Assertions.assertEquals(0, plugins.size());
        Assertions
                .assertEquals(
                        DefaultPluginFactory.class.getName(),
                        mappedPluginFactories.defaultPluginFactoryWithConfig().pluginFactory().getClass().getName()
                );
    }

    @Test
    void testWithInvalidPluginFactory() {
        final Map<String, PluginFactoryConfig> configs = new HashMap<>();
        configs.put("123", new PluginFactoryConfigImpl("invalid", "path"));
        final MappedPluginFactories mappedPluginFactories = new MappedPluginFactories(
                configs,
                "invalid",
                "host",
                new SyslogConfig("app", "host"),
                Logger.getAnonymousLogger()
        );

        Assertions.assertThrows(IllegalStateException.class, mappedPluginFactories::asUnmodifiableMap);
        Assertions.assertThrows(IllegalStateException.class, mappedPluginFactories::defaultPluginFactoryWithConfig);
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier
                .forClass(MappedPluginFactories.class)
                .withPrefabValues(Logger.class, Logger.getAnonymousLogger(), Logger.getAnonymousLogger())
                .withIgnoredFields("logger")
                .verify();
    }
}
