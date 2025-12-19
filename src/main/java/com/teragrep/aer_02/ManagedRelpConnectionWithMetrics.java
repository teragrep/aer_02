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
package com.teragrep.aer_02;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.client.IManagedRelpConnection;
import com.teragrep.rlp_01.client.IRelpConnection;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static com.codahale.metrics.MetricRegistry.name;

public final class ManagedRelpConnectionWithMetrics implements IManagedRelpConnection {

    private final Logger logger;
    private final IRelpConnection relpConnection;
    private boolean hasConnected;

    // metrics
    private final Counter records;
    private final Counter resends;
    private final Counter connects;
    private final Counter retriedConnects;
    private final Timer sendLatency;
    private final Timer connectLatency;

    public ManagedRelpConnectionWithMetrics(
            final Logger logger,
            final IRelpConnection relpConnection,
            final String name,
            final MetricRegistry metricRegistry
    ) {
        this(
                logger,
                relpConnection,
                name,
                metricRegistry,
                new SlidingWindowReservoir(10000),
                new SlidingWindowReservoir(10000)
        );
    }

    public ManagedRelpConnectionWithMetrics(
            final Logger logger,
            final IRelpConnection relpConnection,
            final String name,
            final MetricRegistry metricRegistry,
            final Reservoir sendReservoir,
            final Reservoir connectReservoir
    ) {
        this.logger = logger;
        this.relpConnection = relpConnection;

        this.records = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "records"));
        this.resends = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "resends"));
        this.connects = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "connects"));
        this.retriedConnects = metricRegistry.counter(name(DefaultOutput.class, "<[" + name + "]>", "retriedConnects"));
        this.sendLatency = metricRegistry
                .timer(name(DefaultOutput.class, "<[" + name + "]>", "sendLatency"), () -> new Timer(sendReservoir));
        this.connectLatency = metricRegistry
                .timer(name(DefaultOutput.class, "<[" + name + "]>", "connectLatency"), () -> new Timer(connectReservoir));

        this.hasConnected = false;
    }

    @Override
    public void forceReconnect() {
        tearDown();
        connect();
    }

    @Override
    public void reconnect() {
        close();
        connect();
    }

    @Override
    public void connect() {
        boolean connected = false;
        while (!connected) {
            final Timer.Context context = connectLatency.time();
            try {
                this.hasConnected = true;
                connected = relpConnection
                        .connect(relpConnection.relpConfig().relpTarget, relpConnection.relpConfig().relpPort);

                context.close();
                connects.inc();
            }
            catch (IOException | TimeoutException e) {
                logger
                        .warning(
                                "Failed to connect to relp server <[" + relpConnection.relpConfig().relpTarget + "]>:<["
                                        + relpConnection.relpConfig().relpPort + "]>: <" + e.getMessage() + ">"
                        );

                try {
                    Thread.sleep(relpConnection.relpConfig().relpReconnectInterval);
                    retriedConnects.inc();
                }
                catch (InterruptedException exception) {
                    logger.warning("Reconnection timer interrupted, reconnecting now");
                }
            }
        }
    }

    private void tearDown() {
        /*
         TODO remove: wouldn't need a check hasConnected but there is a bug in RLP-01 tearDown()
         see https://github.com/teragrep/rlp_01/issues/63 for further info
         */
        if (hasConnected) {
            relpConnection.tearDown();
        }
    }

    @Override
    public void ensureSent(final RelpBatch relpBatch) {
        final long numRecords = relpBatch.getWorkQueueLength();
        try (final Timer.Context context = sendLatency.time()) {
            // avoid unnecessary exception for fresh connections
            if (!hasConnected) {
                connect();
            }

            boolean notSent = true;
            while (notSent) {
                try {
                    relpConnection.commit(relpBatch);

                    records.inc(numRecords);
                }
                catch (IllegalStateException | IOException | TimeoutException e) {
                    logger.warning("Exception <" + e.getMessage() + "> while sending relpBatch. Will retry");
                }
                if (!relpBatch.verifyTransactionAll()) {
                    relpBatch.retryAllFailed();
                    resends.inc(1);
                    this.tearDown();
                    this.connect();
                }
                else {
                    notSent = false;
                }
            }
        }
    }

    @Override
    public void ensureSent(final byte[] bytes) {
        final RelpBatch relpBatch = new RelpBatch();
        relpBatch.insert(bytes);
        ensureSent(relpBatch);
    }

    @Override
    public boolean isStub() {
        return false;
    }

    @Override
    public void close() {
        try {
            this.relpConnection.disconnect();
        }
        catch (IllegalStateException | IOException | TimeoutException e) {
            logger.warning("Forcefully closing connection due to exception <" + e.getMessage() + ">");
        }
        finally {
            tearDown();
        }
    }
}
