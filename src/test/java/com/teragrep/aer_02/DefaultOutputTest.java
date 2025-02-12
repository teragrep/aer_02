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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.teragrep.aer_02.config.RelpConnectionConfig;
import com.teragrep.aer_02.config.source.PropertySource;
import com.teragrep.aer_02.fakes.ConnectionlessRelpConnectionFake;
import com.teragrep.rlp_01.client.ManagedRelpConnectionStub;
import com.teragrep.aer_02.fakes.RelpConnectionFake;
import com.teragrep.aer_02.fakes.ThrowingRelpConnectionFake;
import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;
import com.teragrep.rlp_01.client.IManagedRelpConnection;
import com.teragrep.rlp_01.client.RelpConnectionWithConfig;
import com.teragrep.rlp_01.pool.UnboundPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static com.codahale.metrics.MetricRegistry.name;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DefaultOutputTest {

    @Test
    public void testSendLatencyMetricIsCapped() { // Should only keep information on the last 10.000 messages
        SyslogMessage syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withMsgId("123")
                .withMsg("test");

        final int measurementLimit = 10000;

        // set up DefaultOutput
        MetricRegistry metricRegistry = new MetricRegistry();
        SlidingWindowReservoir sendReservoir = new SlidingWindowReservoir(measurementLimit);
        SlidingWindowReservoir connectReservoir = new SlidingWindowReservoir(measurementLimit);

        UnboundPool<IManagedRelpConnection> pool = new UnboundPool<>(
                () -> new ManagedRelpConnectionWithMetrics(
                        Logger.getAnonymousLogger(),
                        new RelpConnectionWithConfig(
                                new RelpConnectionFake(),
                                new RelpConnectionConfig(new PropertySource()).asRelpConfig()
                        ),
                        "defaultOutput",
                        metricRegistry,
                        sendReservoir,
                        connectReservoir
                ),
                new ManagedRelpConnectionStub()
        );

        DefaultOutput output = new DefaultOutput(Logger.getAnonymousLogger(), pool);

        try {
            for (int i = 0; i < measurementLimit + 100; i++) { // send more messages than the limit is
                output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
            }
        }
        finally {
            output.close();
        }

        Assertions.assertEquals(measurementLimit, sendReservoir.size()); // should have measurementLimit amount of records saved
        Assertions.assertEquals(1, connectReservoir.size()); // only connected once
    }

    @Test
    public void testConnectionLatencyMetricIsCapped() { // Should take information on how long it took to successfully connect
        System.setProperty("relp.connection.retry.interval", "1");

        SyslogMessage syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withMsgId("123")
                .withMsg("test");

        final int measurementLimit = 100;
        final int reconnections = measurementLimit + 10;

        // set up DefaultOutput
        MetricRegistry metricRegistry = new MetricRegistry();
        SlidingWindowReservoir sendReservoir = new SlidingWindowReservoir(measurementLimit);
        SlidingWindowReservoir connectReservoir = new SlidingWindowReservoir(measurementLimit);

        UnboundPool<IManagedRelpConnection> pool = new UnboundPool<>(
                () -> new ManagedRelpConnectionWithMetrics(
                        Logger.getAnonymousLogger(),
                        new RelpConnectionWithConfig(new ConnectionlessRelpConnectionFake(reconnections), new RelpConnectionConfig(new PropertySource()).asRelpConfig()), "defaultOutput", metricRegistry, sendReservoir, connectReservoir
                ),
                new ManagedRelpConnectionStub()
        );

        DefaultOutput output = new DefaultOutput(Logger.getAnonymousLogger(), pool);

        try {
            output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
        }
        finally {
            output.close();
        }

        Assertions.assertEquals(1, sendReservoir.size()); // only sent 1 message
        Assertions.assertEquals(measurementLimit, connectReservoir.size()); // should have measurementLimit amount of records saved

        System.clearProperty("relp.connection.retry.interval");
    }

    @Test
    public void testConnectionLatencyMetricWithException() { // should not update value if an exception was thrown from server
        System.setProperty("relp.connection.retry.interval", "1");

        SyslogMessage syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withMsgId("123")
                .withMsg("test");

        final int reconnections = 10;
        // set up DefaultOutput
        MetricRegistry metricRegistry = new MetricRegistry();
        UnboundPool<IManagedRelpConnection> pool = new UnboundPool<>(
                () -> new ManagedRelpConnectionWithMetrics(
                        Logger.getAnonymousLogger(),
                        new RelpConnectionWithConfig(new ThrowingRelpConnectionFake(reconnections), new RelpConnectionConfig(new PropertySource()).asRelpConfig()), "defaultOutput", metricRegistry
                ),
                new ManagedRelpConnectionStub()
        );

        DefaultOutput output = new DefaultOutput(Logger.getAnonymousLogger(), pool);

        try {
            output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
        }
        finally {
            output.close();
        }

        Timer sendTimer = metricRegistry.timer(name(DefaultOutput.class, "<[defaultOutput]>", "sendLatency"));
        Timer connectionTimer = metricRegistry.timer(name(DefaultOutput.class, "<[defaultOutput]>", "connectLatency"));

        Assertions.assertEquals(1, sendTimer.getCount()); // only sent 1 message
        Assertions.assertEquals(1, connectionTimer.getCount()); // only 1 connection attempt without throwing recorded

        System.clearProperty("relp.connection.retry.interval");
    }
}
