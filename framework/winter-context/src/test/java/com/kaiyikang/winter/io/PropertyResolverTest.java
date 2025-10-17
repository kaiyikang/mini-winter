package com.kaiyikang.winter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyResolverTest {

    @Test
    public void propertyValue() {
        // Given
        var props = new Properties();
        props.setProperty("app.title", "Winter Framework");
        props.setProperty("app.version", "v1.0");

        props.setProperty("jdbc.url", "jdbc:mysql://localhost:1234/ok");
        props.setProperty("jdbc.username", "kang");

        props.setProperty("jdbc.password", "0a0c0d");
        props.setProperty("jdbc.pool-size", "20");
        props.setProperty("jdbc.auto-commit", "true");

        props.setProperty("scheduler.started-at", "2023-03-29T21:45:01");
        props.setProperty("scheduler.backup-at", "03:05:10");

        props.setProperty("scheduler.cleanup", "P2DT8H21M");

        // When
        var pr = new PropertyResolver(props);

        assertEquals("Winter Framework", pr.getProperty("app.title"));
        assertEquals("v1.0", pr.getProperty("app.version"));
        assertEquals("v1.0", pr.getProperty("app.version", "unknown"));
        assertNull(pr.getProperty("app.author"));
        assertEquals("yikai", pr.getProperty("app.author", "yikai"));

        assertTrue(pr.getProperty("jdbc.auto-commit", boolean.class));
        assertEquals(Boolean.TRUE, pr.getProperty("jdbc.auto-commit", Boolean.class));
        assertTrue(pr.getProperty("jdbc.detect-leck", boolean.class, true));

        assertEquals(20, pr.getProperty("jdbc.pool-size", int.class, 999));
        assertEquals(5, pr.getProperty("jdbc.idle", int.class, 5));

        assertEquals(LocalDateTime.parse("2023-03-29T21:45:01"),
                pr.getProperty("scheduler.started-at", LocalDateTime.class));
        assertEquals(LocalTime.parse("03:05:10"), pr.getProperty("scheduler.backup-at", LocalTime.class));
        assertEquals(LocalTime.parse("23:59:59"),
                pr.getProperty("scheduler.restart-at", LocalTime.class, LocalTime.parse("23:59:59")));
        assertEquals(Duration.ofMinutes((2 * 24 + 8) * 60 + 21), pr.getProperty("scheduler.cleanup", Duration.class));
    }

    @Test
    public void requiredProperty() {
        var props = new Properties();
        props.setProperty("app.title", "Winter Framework");

        var pr = new PropertyResolver(props);
        assertThrows(NullPointerException.class, () -> {
            pr.getRequiredProperty("not.exist");
        });
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void propertyHolder() {

        // Given
        String home = System.getenv("HOME");
        System.out.println("env HOME= " + " " + home);
        var props = new Properties();
        props.setProperty("app.title", "Winter Framework");

        // When
        var pr = new PropertyResolver(props);

        // Then
        assertEquals("Winter Framework", pr.getProperty("${app.title}"));
        assertThrows(NullPointerException.class, () -> {
            pr.getProperty("${app.version}");
        });

        assertEquals("v1.0", pr.getProperty("${app.version:v1.0}"));
        assertEquals(1, pr.getProperty("${app.version:1}", Integer.class));
        assertThrows(NumberFormatException.class, () -> {
            pr.getProperty("${app.version:x}", Integer.class);
        });

        assertEquals(home, pr.getProperty("${app.path:${HOME}}"));
        assertEquals(home, pr.getProperty("${app.path:${app.home:${HOME}}}"));
        assertEquals("/not-exist", pr.getProperty("${app.path:${app.home:${ENV_NOT_EXIST:/not-exist}}}"));
    }
}
