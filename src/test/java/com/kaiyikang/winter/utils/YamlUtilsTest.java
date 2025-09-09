package com.kaiyikang.winter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class YamlUtilsTest {

    @Test
    public void testLoadYaml() {
        // Given + When
        final Map<String, Object> configs = YamlUtils.loadYamlAsPlainMap("/application.yaml");
        for (String key : configs.keySet()) {
            Object value = configs.get(key);
            System.out.println(key + ": " + value + " (" + value.getClass() + ")");
        }

        // Then
        assertEquals("Winter Framework", configs.get("app.title"));
        assertEquals("1.0.0", configs.get("app.version"));
        assertEquals(null, configs.get("app.author"));

        assertEquals("${AUTO_COMMIT:false}", configs.get("winter.datasource.auto-commit"));
        assertEquals("level-4", configs.get("other.deep.deep.level"));

    }
}
