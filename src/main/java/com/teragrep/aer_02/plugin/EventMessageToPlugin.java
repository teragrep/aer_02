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
import com.teragrep.aer_02.config.SyslogConfig;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.aer_02.json.JsonResourceId;
import com.teragrep.akv_01.plugin.*;
import jakarta.json.Json;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;

public final class EventMessageToPlugin {

    private final String unrefinedMsg;
    private final Sourceable configSource;
    private final Map<String, PluginFactoryConfig> pluginFactoryConfigs;
    private final String defaultPluginFactoryClassName;
    private final String hostname;
    private final ExecutionContext context;

    public EventMessageToPlugin(
            final String unrefinedMsg,
            final Sourceable configSource,
            final Map<String, PluginFactoryConfig> pluginFactoryConfigs,
            final String defaultPluginFactoryClassName,
            final String hostname,
            final ExecutionContext context
    ) {
        this.unrefinedMsg = unrefinedMsg;
        this.configSource = configSource;
        this.pluginFactoryConfigs = pluginFactoryConfigs;
        this.defaultPluginFactoryClassName = defaultPluginFactoryClassName;
        this.hostname = hostname;
        this.context = context;
    }

    public Plugin toPlugin() {
        final String resourceId = new JsonResourceId(unrefinedMsg).resourceId();
        final SyslogConfig syslogConfig = new SyslogConfig(configSource);
        final PluginFactoryConfig pluginConfig = pluginFactoryConfigs
                .getOrDefault(
                        resourceId,
                        new PluginFactoryConfigImpl(
                                defaultPluginFactoryClassName,
                                Json.createObjectBuilder().add("realHostname", hostname).add("syslogHostname", syslogConfig.hostName()).add("syslogAppname", syslogConfig.appName()).build().toString()
                        )
                );
        final PluginFactory pluginFactory;
        try {
            pluginFactory = new PluginFactoryInitialization(pluginConfig.pluginFactoryClassName()).pluginFactory();
        }
        catch (
                ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException
                | IllegalAccessException e
        ) {
            context.getLogger().throwing(EventMessageToPlugin.class.getName(), "toPlugin", e);
            throw new IllegalStateException(e);
        }

        return pluginFactory.plugin(pluginConfig.configPath());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventMessageToPlugin that = (EventMessageToPlugin) o;
        return Objects.equals(unrefinedMsg, that.unrefinedMsg) && Objects.equals(configSource, that.configSource)
                && Objects.equals(pluginFactoryConfigs, that.pluginFactoryConfigs) && Objects.equals(defaultPluginFactoryClassName, that.defaultPluginFactoryClassName) && Objects.equals(hostname, that.hostname) && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(unrefinedMsg, configSource, pluginFactoryConfigs, defaultPluginFactoryClassName, hostname, context);
    }
}
