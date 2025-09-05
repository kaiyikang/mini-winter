package com.kaiyikang.winter.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class ResrouceResolverTest {

    @Test
    public void scanClass() {

    }

    @Test
    public void scanJar() {
        final var pkg = PostConstruct.class.getPackageName();
        final ResourceResolver resolver = new ResourceResolver(pkg);
        List<String> classes = resolver.scan(
                res -> {
                    String name = res.name();
                    if (name.endsWith(".class")) {
                        return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", "/");
                    }
                    return null;
                });
        assertTrue(classes.contains(PostConstruct.class.getName()));
        assertTrue(classes.contains(PreDestroy.class.getName()));
    }

    @Test
    public void scanTxt() {
        final var pkg = "com.kaiyikang.scan";
        final ResourceResolver resolver = new ResourceResolver(pkg);
        List<String> classes = resolver.scan(
                res -> {
                    String name = res.name();
                    if (name.endsWith(".txt")) {
                        return name.replace("\\", "/");
                    }
                    return null;
                });
        Collections.sort(classes);
        assertArrayEquals(new String[] {
                "com/kaiyikang/scan/sub1/sub1.txt",
                "com/kaiyikang/scan/sub1/sub2/sub2.txt"
        }, classes.toArray(String[]::new));
    }

}
