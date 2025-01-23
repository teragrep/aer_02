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

import com.codahale.metrics.MetricRegistry;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_01.client.*;
import java.util.logging.Logger;

import java.util.function.Supplier;

public class ManagedRelpConnectionWithMetricsFactory implements Supplier<IManagedRelpConnection> {

    private final RelpConfig relpConfig;
    private final SocketConfig socketConfig;
    private final SSLContextSupplier sslContextSupplier;
    private final String name;
    private final MetricRegistry metricRegistry;
    private final Logger logger;

    public ManagedRelpConnectionWithMetricsFactory(
            Logger logger,
            String name,
            MetricRegistry metricRegistry,
            RelpConfig relpConfig
    ) {
        this(logger, relpConfig, name, metricRegistry, new SocketConfigDefault());
    }

    public ManagedRelpConnectionWithMetricsFactory(
            Logger logger,
            RelpConfig relpConfig,
            String name,
            MetricRegistry metricRegistry,
            SocketConfig socketConfig
    ) {
        this(logger, relpConfig, name, metricRegistry, socketConfig, new SSLContextSupplierStub());
    }

    public ManagedRelpConnectionWithMetricsFactory(
            Logger logger,
            RelpConfig relpConfig,
            String name,
            MetricRegistry metricRegistry,
            SSLContextSupplier sslContextSupplier
    ) {
        this(logger, relpConfig, name, metricRegistry, new SocketConfigDefault(), sslContextSupplier);
    }

    public ManagedRelpConnectionWithMetricsFactory(
            Logger logger,
            RelpConfig relpConfig,
            String name,
            MetricRegistry metricRegistry,
            SocketConfig socketConfig,
            SSLContextSupplier sslContextSupplier
    ) {
        this.logger = logger;
        this.relpConfig = relpConfig;
        this.name = name;
        this.metricRegistry = metricRegistry;
        this.socketConfig = socketConfig;
        this.sslContextSupplier = sslContextSupplier;
    }

    @Override
    public IManagedRelpConnection get() {
        logger.info("get() called for new IManagedRelpConnection");
        IRelpConnection relpConnection;
        if (sslContextSupplier.isStub()) {
            relpConnection = new RelpConnectionWithConfig(new RelpConnection(), relpConfig);
        }
        else {
            relpConnection = new RelpConnectionWithConfig(
                    new RelpConnection(() -> sslContextSupplier.get().createSSLEngine()),
                    relpConfig
            );
        }

        relpConnection.setReadTimeout(socketConfig.readTimeout());
        relpConnection.setWriteTimeout(socketConfig.writeTimeout());
        relpConnection.setConnectionTimeout(socketConfig.connectTimeout());
        relpConnection.setKeepAlive(socketConfig.keepAlive());

        IManagedRelpConnection managedRelpConnection = new ManagedRelpConnectionWithMetrics(
                logger,
                relpConnection,
                name,
                metricRegistry
        );

        if (relpConfig.rebindEnabled) {
            managedRelpConnection = new RebindableRelpConnection(managedRelpConnection, relpConfig.rebindRequestAmount);
        }

        if (relpConfig.maxIdleEnabled) {
            managedRelpConnection = new RenewableRelpConnection(managedRelpConnection, relpConfig.maxIdle);
        }
        logger.info("returning new managedRelpConnection");
        return managedRelpConnection;
    }
}
