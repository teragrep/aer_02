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
package com.teragrep.aer_02.fakes;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;

import java.util.HashMap;
import java.util.Map;

public class HttpResponseMessageBuilderFake implements HttpResponseMessage.Builder {

    private final HttpStatusType httpStatus;
    private final Map<String, String> headers;
    private final Object body;

    public HttpResponseMessageBuilderFake() {
        this.httpStatus = HttpStatus.OK;
        this.headers = new HashMap<>();
        this.body = "";
    }

    public HttpResponseMessageBuilderFake(HttpStatusType httpStatus, Map<String, String> headers, Object body) {
        this.httpStatus = httpStatus;
        this.headers = headers;
        this.body = body;
    }

    @Override
    public HttpResponseMessage.Builder status(HttpStatusType httpStatusType) {
        return new HttpResponseMessageBuilderFake(httpStatusType, headers, body);
    }

    @Override
    public HttpResponseMessage.Builder header(String s, String s1) {
        Map<String, String> newHeaders = new HashMap<>(headers);
        newHeaders.put(s, s1);
        return new HttpResponseMessageBuilderFake(httpStatus, newHeaders, body);
    }

    @Override
    public HttpResponseMessage.Builder body(Object o) {
        return new HttpResponseMessageBuilderFake(httpStatus, headers, o);
    }

    @Override
    public HttpResponseMessage build() {
        return new HttpResponseMessageFake(body, headers, httpStatus);
    }
}
