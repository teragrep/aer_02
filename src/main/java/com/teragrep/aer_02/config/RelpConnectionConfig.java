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
package com.teragrep.aer_02.config;

import com.teragrep.aer_02.config.source.Sourceable;
import com.teragrep.rlp_01.client.RelpConfig;
import com.teragrep.rlp_01.client.SocketConfig;
import com.teragrep.rlp_01.client.SocketConfigImpl;

import java.time.Duration;

public final class RelpConnectionConfig {

    private static final int DEFAULT_CONNECTION_TIMEOUT = 2500;
    private static final int DEFAULT_TRANSACTION_READ_TIMEOUT = 1500;
    private static final int DEFAULT_TRANSACTION_WRITE_TIMEOUT = 1500;
    private static final int DEFAULT_RETRY_INTERVAL = 500;
    private static final int DEFAULT_CONNECTION_PORT = 601;
    private static final String DEFAULT_CONNECTION_ADDRESS = "localhost";
    private static final int DEFAULT_REBIND_AMOUNT = 100000;
    private static final String DEFAULT_REBIND_ENABLED = "true";
    private static final long DEFAULT_IDLE_DURATION = 150000L;
    private static final String DEFAULT_IDLE_ENABLED = "false";
    private static final String DEFAULT_CONNECTION_KEEPALIVE = "true";

    private final int connectTimeout;
    private final int readTimeout;
    private final int writeTimeout;
    private final int reconnectInterval;
    private final int port;
    private final String address;
    private final int rebindRequestAmount;
    private final boolean rebindEnabled;
    private final Duration maxIdle;
    private final boolean maxIdleEnabled;
    private final boolean keepAlive;

    public RelpConnectionConfig(final Sourceable configSource) {
        this(
                Integer
                        .parseInt(
                                configSource.source("relp.connection.timeout", String.valueOf(DEFAULT_CONNECTION_TIMEOUT))
                        ),
                Integer
                        .parseInt(
                                configSource
                                        .source(
                                                "relp.transaction.read.timeout",
                                                String.valueOf(DEFAULT_TRANSACTION_READ_TIMEOUT)
                                        )
                        ),
                Integer
                        .parseInt(
                                configSource
                                        .source(
                                                "relp.transaction.write.timeout",
                                                String.valueOf(DEFAULT_TRANSACTION_WRITE_TIMEOUT)
                                        )
                        ),
                Integer
                        .parseInt(
                                configSource
                                        .source("relp.connection.retry.interval", String.valueOf(DEFAULT_RETRY_INTERVAL))
                        ),
                Integer.parseInt(configSource.source("relp.connection.port", String.valueOf(DEFAULT_CONNECTION_PORT))),
                configSource.source("relp.connection.address", DEFAULT_CONNECTION_ADDRESS),
                Integer.parseInt(configSource.source("relp.rebind.request.amount", String.valueOf(DEFAULT_REBIND_AMOUNT))), Boolean.parseBoolean(configSource.source("relp.rebind.enabled", DEFAULT_REBIND_ENABLED)), Duration.parse(configSource.source("relp.max.idle.duration", Duration.ofMillis(DEFAULT_IDLE_DURATION).toString())), Boolean.parseBoolean(configSource.source("relp.max.idle.enabled", DEFAULT_IDLE_ENABLED)), Boolean.parseBoolean(configSource.source("relp.connection.keepalive", DEFAULT_CONNECTION_KEEPALIVE))

        );
    }

    public RelpConnectionConfig(
            final int connectTimeout,
            final int readTimeout,
            final int writeTimeout,
            final int reconnectInt,
            final int port,
            final String addr,
            final int rebindRequestAmount,
            final boolean rebindEnabled,
            final Duration maxIdle,
            final boolean maxIdleEnabled,
            final boolean keepAlive
    ) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.reconnectInterval = reconnectInt;
        this.port = port;
        this.address = addr;
        this.rebindRequestAmount = rebindRequestAmount;
        this.rebindEnabled = rebindEnabled;
        this.maxIdle = maxIdle;
        this.maxIdleEnabled = maxIdleEnabled;
        this.keepAlive = keepAlive;
    }

    /**
     * @return relp.connection.timeout
     */
    public int connectTimeout() {
        return connectTimeout;
    }

    /**
     * @return relp.transaction.read.timeout
     */
    public int readTimeout() {
        return readTimeout;
    }

    /**
     * @return relp.transaction.write.timeout
     */
    public int writeTimeout() {
        return writeTimeout;
    }

    /**
     * @return relp.connection.retry.interval
     */
    public int reconnectInterval() {
        return reconnectInterval;
    }

    /**
     * @return relp.connection.port
     */
    public int relpPort() {
        return port;
    }

    /**
     * @return relp.connection.address
     */
    public String relpAddress() {
        return address;
    }

    public boolean maxIdleEnabled() {
        return maxIdleEnabled;
    }

    public Duration maxIdle() {
        return maxIdle;
    }

    public boolean rebindEnabled() {
        return rebindEnabled;
    }

    public int rebindRequestAmount() {
        return rebindRequestAmount;
    }

    public boolean keepAlive() {
        return keepAlive;
    }

    public RelpConfig asRelpConfig() {
        return new RelpConfig(
                relpAddress(),
                relpPort(),
                reconnectInterval(),
                rebindRequestAmount(),
                rebindEnabled(),
                maxIdle(),
                maxIdleEnabled()
        );
    }

    public SocketConfig asSocketConfig() {
        return new SocketConfigImpl(readTimeout(), writeTimeout(), connectTimeout(), keepAlive());
    }
}
