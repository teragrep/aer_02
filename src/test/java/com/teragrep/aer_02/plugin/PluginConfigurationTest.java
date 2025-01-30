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
package com.teragrep.aer_02.plugin;

import com.teragrep.aer_02.fakes.SourceableFake;
import jakarta.json.JsonStructure;
import jakarta.json.stream.JsonParsingException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public final class PluginConfigurationTest {

    @Test
    void testPluginConfigurationDefault() {
        PluginConfiguration pluginCfg = new PluginConfiguration(new SourceableFake());
        JsonStructure js = Assertions.assertDoesNotThrow(pluginCfg::asJson);

        // Should default to com.teragrep.aer_02.plugin.DefaultPluginFactory
        Assertions
                .assertEquals(
                        "com.teragrep.aer_02.plugin.DefaultPluginFactory",
                        js.asJsonObject().getString("defaultPluginFactoryClass")
                );
    }

    @Test
    void testPluginConfigurationFromFile() {
        Map<String, String> config = new HashMap<>();
        config.put("plugins.config.path", "src/test/resources/plugincfg.json");
        PluginConfiguration pluginCfg = new PluginConfiguration(new SourceableFake(config));
        JsonStructure js = Assertions.assertDoesNotThrow(pluginCfg::asJson);

        // Should default to com.teragrep.aer_02.fakes.ThrowingPluginFactory
        Assertions
                .assertEquals(
                        "com.teragrep.aer_02.fakes.ThrowingPluginFactory",
                        js.asJsonObject().getString("defaultPluginFactoryClass")
                );
        // Should also have com.teragrep.aer_02.plugin.DefaultPluginFactory for resourceId="123"
        Assertions
                .assertEquals("123", js.asJsonObject().getJsonArray("resourceIds").getJsonObject(0).getString("resourceId"));
        Assertions
                .assertEquals(
                        "com.teragrep.aer_02.plugin.DefaultPluginFactory", js.asJsonObject().getJsonArray("resourceIds").getJsonObject(0).getString("pluginFactoryClass")
                );
    }

    @Test
    void testMissingConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("plugins.config.path", "src/test/resources/missing_config.json");
        PluginConfiguration pluginCfg = new PluginConfiguration(new SourceableFake(config));
        Assertions.assertThrows(FileNotFoundException.class, pluginCfg::asJson);
    }

    @Test
    void testInvalidSyntaxConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("plugins.config.path", "src/test/resources/invalid_syntax_config.json");
        PluginConfiguration pluginCfg = new PluginConfiguration(new SourceableFake(config));
        Assertions.assertThrows(JsonParsingException.class, pluginCfg::asJson);
    }

    @Test
    void testEqualsContract() {
        EqualsVerifier.forClass(PluginConfiguration.class).verify();
    }
}
