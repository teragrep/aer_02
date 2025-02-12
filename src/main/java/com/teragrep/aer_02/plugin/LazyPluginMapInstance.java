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

import com.teragrep.aer_02.Hostname;
import com.teragrep.aer_02.config.SyslogConfig;
import com.teragrep.aer_02.config.source.EnvironmentSource;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.akv_01.plugin.PluginFactoryConfig;
import com.teragrep.akv_01.plugin.PluginMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Uses Initialization on demand holder idiom. See
 * <a href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">Wikipedia article</a> for more details.
 */
public final class LazyPluginMapInstance {

    private final Map<String, WrappedPluginFactoryWithConfig> pluginFactories;
    private final WrappedPluginFactoryWithConfig defaultPluginFactory;
    private final WrappedPluginFactoryWithConfig exceptionPluginFactory;

    private LazyPluginMapInstance() {
        final Logger logger = Logger.getAnonymousLogger();
        final Sourceable configSource = new EnvironmentSource();
        final SyslogConfig syslogConfig = new SyslogConfig(configSource);
        final String hostname = new Hostname("localhost").hostname();
        final PluginMap pluginMap;
        try {
            pluginMap = new PluginMap(new PluginConfiguration(configSource).asJson());
        }
        catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final Map<String, PluginFactoryConfig> pluginFactoryConfigs = pluginMap.asUnmodifiableMap();
        final String defaultPluginFactoryClassName = pluginMap.defaultPluginFactoryClassName();
        final String exceptionPluginFactoryClassName = pluginMap.exceptionPluginFactoryClassName();
        final MappedPluginFactories mappedPluginFactories = new MappedPluginFactories(
                pluginFactoryConfigs,
                defaultPluginFactoryClassName,
                exceptionPluginFactoryClassName,
                hostname,
                syslogConfig,
                logger
        );

        this.pluginFactories = mappedPluginFactories.asUnmodifiableMap();
        this.defaultPluginFactory = mappedPluginFactories.defaultPluginFactoryWithConfig();
        this.exceptionPluginFactory = mappedPluginFactories.exceptionPluginFactoryWithConfig();
    }

    public static LazyPluginMapInstance lazySingletonInstance() {
        // a way to access the singleton
        return LazyHolder.INSTANCE;
    }

    static final class LazyHolder {

        // this holds the instance, and prevents initialization before access
        static final LazyPluginMapInstance INSTANCE = new LazyPluginMapInstance();
    }

    public Map<String, WrappedPluginFactoryWithConfig> pluginFactories() {
        return pluginFactories;
    }

    public WrappedPluginFactoryWithConfig defaultPluginFactory() {
        return defaultPluginFactory;
    }

    public WrappedPluginFactoryWithConfig exceptionPluginFactory() {
        return exceptionPluginFactory;
    }
}
