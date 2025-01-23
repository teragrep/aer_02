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
import com.teragrep.aer_02.config.RelpConnectionConfig;
import com.teragrep.aer_02.config.source.EnvironmentSource;
import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.aer_02.metrics.JmxReport;
import com.teragrep.aer_02.metrics.PrometheusReport;
import com.teragrep.aer_02.metrics.Report;
import com.teragrep.aer_02.metrics.Slf4jReport;
import com.teragrep.aer_02.tls.AzureSSLContextSupplier;
import com.teragrep.rlp_01.client.IManagedRelpConnection;
import com.teragrep.rlp_01.client.ManagedRelpConnectionStub;
import com.teragrep.rlp_01.pool.Pool;
import com.teragrep.rlp_01.pool.UnboundPool;
import io.prometheus.client.dropwizard.DropwizardExports;

import java.util.logging.Logger;

/**
 * Uses Initialization on demand holder idiom. See
 * <a href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">Wikipedia article</a> for more details.
 */
public final class InitializationOnDemandHolder {

    private InitializationOnDemandHolder() {

    }

    private static final LazyInstance INSTANCE = new LazyInstance();

    public static LazyInstance lazyInstance() {
        return INSTANCE;
    }

    static final class LazyInstance {

        private final DefaultOutput defaultOutput;
        private final MetricRegistry metricRegistry;
        private final Report report;

        private LazyInstance() {
            final Logger logger = Logger.getAnonymousLogger();
            metricRegistry = new MetricRegistry();
            Sourceable environmentSource = new EnvironmentSource();
            RelpConnectionConfig relpConnectionConfig = new RelpConnectionConfig(environmentSource);
            report = new JmxReport(
                    new Slf4jReport(new PrometheusReport(new DropwizardExports(metricRegistry)), metricRegistry),
                    metricRegistry
            );

            report.start();

            Pool<IManagedRelpConnection> relpConnectionPool;
            if (environmentSource.source("relp.tls.mode", "none").equals("keyVault")) {
                logger.info("Using keyVault TLS mode");
                relpConnectionPool = new UnboundPool<>(
                        new ManagedRelpConnectionWithMetricsFactory(
                                logger,
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
                logger.info("Using plain mode");
                relpConnectionPool = new UnboundPool<>(
                        new ManagedRelpConnectionWithMetricsFactory(
                                logger,
                                relpConnectionConfig.asRelpConfig(),
                                "defaultOutput",
                                metricRegistry,
                                relpConnectionConfig.asSocketConfig()
                        ),
                        new ManagedRelpConnectionStub()
                );
            }

            defaultOutput = new DefaultOutput(logger, relpConnectionPool);

            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        }

        public DefaultOutput defaultOutput() {
            return defaultOutput;
        }

        public MetricRegistry metricRegistry() {
            return metricRegistry;
        }

        private void close() {
            report.close();
            defaultOutput.close();
        }
    }
}
