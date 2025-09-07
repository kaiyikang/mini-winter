package com.kaiyikang.winter.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

public class YamlUtils {

    public static Map<String, Object> loadYaml(String path) {
        // 1. Loader Options: Controls how YAML input is parsed/loaded
        var loaderOptions = new LoaderOptions();

        // 2. Exporter Options: Controls how Java objects are serialized/exported as
        // YAML strings
        var dumperOptions = new DumperOptions();
        // 3. Representation: Controls the mapping relationship between Java objects and
        // YAML nodes
        var representer = new Representer(dumperOptions);

        var resolver = new NoImplicitResolver();
        var yaml = new Yaml(new Constructor(loaderOptions), representer, dumperOptions, loaderOptions, resolver);
        return ClassPathUtils.readInputStream(path, (input) -> {
            return (Map<String, Object>) yaml.load(input);
        });
    }

    public static Map<String, Object> loadYamlAsPlainMap(String path) {
        Map<String, Object> data = loadYaml(path);
        Map<String, Object> plain = new LinkedHashMap<>();
        convertTo(data, "", plain);
        return plain;
    }

    static void convertTo(Map<String, Object> source, String prefix, Map<String, Object> plain) {
        for (String key : source.keySet()) {
            Object value = source.get(key);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                convertTo(subMap, prefix + key + ".", plain);
            } else if (value instanceof List) {
                plain.put(prefix + key, value);
            } else {
                plain.put(prefix + key, value.toString());
            }
        }
    }
}

/**
 * Disable ALL implicit convert and treat all values as string.
 */
class NoImplicitResolver extends Resolver {
    public NoImplicitResolver() {
        super();
        super.yamlImplicitResolvers.clear();
    }
}