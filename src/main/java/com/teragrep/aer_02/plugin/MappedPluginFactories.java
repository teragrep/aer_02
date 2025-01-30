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
import com.teragrep.akv_01.plugin.*;
import jakarta.json.Json;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class MappedPluginFactories {

    private final Map<String, PluginFactoryConfig> pluginFactoryConfigs;
    private final String defaultPluginFactoryClassName;
    private final String realHostname;
    private final SyslogConfig syslogConfig;
    private final Logger logger;

    public MappedPluginFactories(
            final Map<String, PluginFactoryConfig> pluginFactoryConfigs,
            final String defaultPluginFactoryClassName,
            final String realHostname,
            final SyslogConfig syslogConfig,
            final Logger logger
    ) {
        this.pluginFactoryConfigs = pluginFactoryConfigs;
        this.defaultPluginFactoryClassName = defaultPluginFactoryClassName;
        this.realHostname = realHostname;
        this.syslogConfig = syslogConfig;
        this.logger = logger;
    }

    public Map<String, WrappedPluginFactoryWithConfig> asUnmodifiableMap() {
        final Map<String, WrappedPluginFactoryWithConfig> rv = new HashMap<>();
        pluginFactoryConfigs.forEach((id, cfg) -> rv.put(id, newWrappedPluginFactoryWithConfig(cfg)));
        return Collections.unmodifiableMap(rv);
    }

    public WrappedPluginFactoryWithConfig defaultPluginFactoryWithConfig() {
        return newWrappedPluginFactoryWithConfig(
                new PluginFactoryConfigImpl(
                        defaultPluginFactoryClassName,
                        Json.createObjectBuilder().add("realHostname", realHostname).add("syslogHostname", syslogConfig.hostName()).add("syslogAppname", syslogConfig.appName()).build().toString()
                )
        );
    }

    private WrappedPluginFactoryWithConfig newWrappedPluginFactoryWithConfig(final PluginFactoryConfig cfg) {
        try {
            return new WrappedPluginFactoryWithConfig(
                    new PluginFactoryInitialization(cfg.pluginFactoryClassName()).pluginFactory(),
                    cfg
            );
        }
        catch (
                ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException
                | IllegalAccessException e
        ) {
            logger.throwing(MappedPluginFactories.class.getName(), "newWrappedPluginFactoryWithConfig", e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MappedPluginFactories that = (MappedPluginFactories) o;
        return Objects.equals(pluginFactoryConfigs, that.pluginFactoryConfigs) && Objects
                .equals(defaultPluginFactoryClassName, that.defaultPluginFactoryClassName)
                && Objects.equals(realHostname, that.realHostname) && Objects.equals(syslogConfig, that.syslogConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginFactoryConfigs, defaultPluginFactoryClassName, realHostname, syslogConfig);
    }
}
