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
import com.teragrep.aer_02.config.RelpConfig;
import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.client.IManagedRelpConnection;
import com.teragrep.rlp_01.client.ManagedRelpConnectionStub;
import com.teragrep.rlp_01.client.RelpConnectionFactory;
import com.teragrep.rlp_01.pool.Pool;
import com.teragrep.rlp_01.pool.UnboundPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;

// TODO unify, this is a copy from cfe_35 which is a copy from rlo_10 with FIXES
final class DefaultOutput implements Output {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOutput.class);

    private final Pool<IManagedRelpConnection> relpConnectionPool;
    //private final RelpConnection relpConnection;
    private final String relpAddress;
    private final int relpPort;
    private final int reconnectInterval;

    // metrics
    private final Counter records;
    private final Counter bytes;
    private final Counter resends;
    private final Counter connects;
    private final Counter retriedConnects;
    private final Timer sendLatency;
    private final Timer connectLatency;

    DefaultOutput(String name, RelpConfig relpConfig, MetricRegistry metricRegistry) {
        this(
                name,
                relpConfig,
                metricRegistry,
                new UnboundPool<>(new RelpConnectionFactory(relpConfig.asRLP01Config()), new ManagedRelpConnectionStub())
        );
    }

    DefaultOutput(
            String name,
            RelpConfig relpConfig,
            MetricRegistry metricRegistry,
            Pool<IManagedRelpConnection> relpConnectionPool
    ) {
        this(
                name,
                relpConfig,
                metricRegistry,
                relpConnectionPool,
                new SlidingWindowReservoir(10000),
                new SlidingWindowReservoir(10000)
        );
    }

    DefaultOutput(
            String name,
            RelpConfig relpConfig,
            MetricRegistry metricRegistry,
            Pool<IManagedRelpConnection> relpConnectionPool,
            Reservoir sendReservoir,
            Reservoir connectReservoir
    ) {
        this.relpAddress = relpConfig.relpAddress();
        this.relpPort = relpConfig.relpPort();
        this.reconnectInterval = relpConfig.reconnectInterval();

        this.relpConnectionPool = relpConnectionPool;

        this.records = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "records"));
        this.bytes = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "bytes"));
        this.resends = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "resends"));
        this.connects = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "connects"));
        this.retriedConnects = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "retriedConnects"));
        this.sendLatency = metricRegistry
                .timer(name(DefaultOutput.class, "<[" + name + "]>", "sendLatency"), () -> new Timer(sendReservoir));
        this.connectLatency = metricRegistry
                .timer(name(DefaultOutput.class, "<[" + name + "]>", "connectLatency"), () -> new Timer(connectReservoir));
    }

    @Override
    public void accept(byte[] syslogMessage) {
        try (final Timer.Context context = sendLatency.time()) {
            RelpBatch batch = new RelpBatch();
            batch.insert(syslogMessage);
            IManagedRelpConnection connection = relpConnectionPool.get();
            connection.ensureSent(syslogMessage);

            // metrics
            // NOTICE these if batch size changes
            records.inc(1);
            bytes.inc(syslogMessage.length);
            relpConnectionPool.offer(connection);
        }
    }

    @Override
    public String toString() {
        return "DefaultOutput{" + "relpAddress='" + relpAddress + '\'' + ", relpPort=" + relpPort + '}';
    }

    @Override
    public void close() {
        relpConnectionPool.close();
    }
}
