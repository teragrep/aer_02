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
import com.microsoft.azure.functions.*;
import com.teragrep.aer_02.fakes.ExecutionContextFake;
import com.teragrep.aer_02.fakes.HttpResponseMessageBuilderFake;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;

public class MetricsTest {

    private final MetricRegistry registry = new MetricRegistry();

    @BeforeEach
    void setup() {
        CollectorRegistry.defaultRegistry.clear();
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(registry));
        registry.counter("test-counter").inc(123);
    }

    @AfterEach
    void teardown() {
        CollectorRegistry.defaultRegistry.clear();
    }

    @Test
    void testHttp() {
        SyslogBridge bridge = new SyslogBridge();
        HttpRequestMessage<Optional<String>> req = new HttpRequestMessage<>() {

            @Override
            public URI getUri() {
                return null;
            }

            @Override
            public HttpMethod getHttpMethod() {
                return HttpMethod.GET;
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "text/plain");
                return h;
            }

            @Override
            public Map<String, String> getQueryParameters() {
                Map<String, String> q = new HashMap<>();
                return q;
            }

            @Override
            public Optional<String> getBody() {
                return Optional.empty();
            }

            @Override
            public HttpResponseMessage.Builder createResponseBuilder(HttpStatus httpStatus) {
                return new HttpResponseMessageBuilderFake();
            }

            @Override
            public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType httpStatusType) {
                return new HttpResponseMessageBuilderFake();
            }
        };

        HttpResponseMessage resp = bridge.metrics(req, new ExecutionContextFake());
        List<String> responseLines = Arrays.asList(resp.getBody().toString().split("\n"));

        Assertions.assertEquals(3, responseLines.size());
        Assertions
                .assertEquals(
                        "# HELP test_counter Generated from Dropwizard metric import (metric=test-counter, type=com.codahale.metrics.Counter)",
                        responseLines.get(0)
                );
        Assertions.assertEquals("# TYPE test_counter gauge", responseLines.get(1));
        Assertions.assertEquals("test_counter 123.0", responseLines.get(2));
    }
}
