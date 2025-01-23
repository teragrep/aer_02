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
package com.teragrep.aer_02;

import com.codahale.metrics.*;
import com.teragrep.aer_02.config.RelpConnectionConfig;
import com.teragrep.aer_02.config.source.DefaultOutputConfig;
import com.teragrep.aer_02.config.source.EnvironmentSource;
import com.teragrep.aer_02.tls.AzureSSLContextSupplier;
import com.teragrep.rlp_01.client.*;
import com.teragrep.rlp_01.pool.Pool;
import com.teragrep.rlp_01.pool.UnboundPool;
import java.util.logging.Logger;

/**
 * Implementation of a shareable output. Required to be thread-safe. Uses Initialization on demand holder idiom. See
 * <a href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">Wikipedia article</a> for more details.
 */
public final class DefaultOutput implements Output {

    private DefaultOutput(DefaultOutputConfig cfg) {
        RelpConnectionConfig relpConnectionConfig = cfg.relpConnectionConfig();
        this.metricRegistry = new MetricRegistry();
        this.relpAddress = relpConnectionConfig.relpAddress();
        this.relpPort = relpConnectionConfig.relpPort();

        if (cfg.tlsMode()) {
            this.relpConnectionPool = new UnboundPool<>(
                    new ManagedRelpConnectionWithMetricsFactory(
                            Logger.getAnonymousLogger(),
                            relpConnectionConfig.asRelpConfig(),
                            "defaultOutput",
                            metricRegistry,
                            relpConnectionConfig.asSocketConfig(),
                            new AzureSSLContextSupplier()
                    ),
                    new ManagedRelpConnectionStub()
            );
        }
        else {
            this.relpConnectionPool = new UnboundPool<>(
                    new ManagedRelpConnectionWithMetricsFactory(
                            Logger.getAnonymousLogger(),
                            relpConnectionConfig.asRelpConfig(),
                            "defaultOutput",
                            metricRegistry,
                            relpConnectionConfig.asSocketConfig()
                    ),
                    new ManagedRelpConnectionStub()
            );
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    private static class DefaultOutputHolder {

        private static final DefaultOutput INSTANCE = new DefaultOutput(
                new DefaultOutputConfig(new EnvironmentSource())
        );
    }

    public static DefaultOutput getInstance() {
        return DefaultOutputHolder.INSTANCE;
    }

    DefaultOutput(
            Logger logger,
            String name,
            RelpConnectionConfig relpConnectionConfig,
            MetricRegistry metricRegistry,
            SSLContextSupplier sslContextSupplier
    ) {
        this(
                logger,
                relpConnectionConfig,
                new UnboundPool<>(
                        new ManagedRelpConnectionWithMetricsFactory(
                                logger,
                                relpConnectionConfig.asRelpConfig(),
                                name,
                                metricRegistry,
                                relpConnectionConfig.asSocketConfig(),
                                sslContextSupplier
                        ),
                        new ManagedRelpConnectionStub()
                ),
                metricRegistry
        );
    }

    DefaultOutput(
            Logger logger,
            RelpConnectionConfig relpConnectionConfig,
            Pool<IManagedRelpConnection> relpConnectionPool,
            MetricRegistry metricRegistry
    ) {
        this.relpAddress = relpConnectionConfig.relpAddress();
        this.relpPort = relpConnectionConfig.relpPort();

        this.relpConnectionPool = relpConnectionPool;
        this.metricRegistry = metricRegistry;
        logger.info("DefaultOutput constructor done");
    }

    private final Pool<IManagedRelpConnection> relpConnectionPool;
    private final String relpAddress;
    private final int relpPort;
    private final MetricRegistry metricRegistry;

    @Override
    public void accept(byte[] syslogMessage) {
        IManagedRelpConnection connection = relpConnectionPool.get();
        connection.ensureSent(syslogMessage);
        relpConnectionPool.offer(connection);
    }

    @Override
    public String toString() {
        return "DefaultOutput{" + "relpAddress='" + relpAddress + '\'' + ", relpPort=" + relpPort + '}';
    }

    @Override
    public void close() {
        relpConnectionPool.close();
    }

    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }
}
