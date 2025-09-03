package com.kaiyikang.winter.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A classpath scan for both directory and jar.
 * 
 */

public class ResourceResolver {
    Logger logger = LoggerFactory.getLogger(getClass());

    final String basePackage;

    public ResourceResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    public <R> List<R> scan(Function<Resource, R> mapper) {
        String basePackagePath = this.basePackage.replace(".", "/");
        String currentScanPath = basePackagePath;

        try {
            List<R> collector = new ArrayList<>();
            scan0(basePackagePath, currentScanPath, collector, mapper);
            return collector;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    <R> void scan0(String basePackagePath, String currentScanPath, List<R> collector, Function<Resource, R> mapper)
            throws IOException, URISyntaxException {
        logger.atDebug().log("scan path: {}", currentScanPath);

        Enumeration<URL> resources = getContextClassLoader().getResources(currentScanPath);

        while (resources.hasMoreElements()) {
            final URL resourceUrl = resources.nextElement();
            final URI resourceUri = resourceUrl.toURI();

            String resourceUriStr = removeTrailingSlash(uriToString(resourceUri));

            // basePackagePath: "com/example/service"
            // resourceUriStr: "file:/D:/project/target/classes/com/example/service"
            // resourceBaseUriStr: "file:/D:/project/target/classes/"
            String resourceBaseUriStr = resourceUriStr.substring(0, resourceUriStr.length() - basePackagePath.length());

            if (resourceBaseUriStr.startsWith("file:")) {
                resourceBaseUriStr = resourceBaseUriStr.substring(5);
            }

            if (resourceUriStr.startsWith("jar:")) {
                final Path jarInternalPath = jarUriToPath(resourceUri, basePackagePath);
                scanFile(true, resourceBaseUriStr, jarInternalPath, collector, mapper);
            } else {
                final Path fileSystemPath = Paths.get(resourceUri);
                scanFile(false, resourceBaseUriStr, fileSystemPath, collector, mapper);
            }
        }
    }

    ClassLoader getContextClassLoader() {
        ClassLoader classLoader = null;
        classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        return classLoader;
    }

    <R> void scanFile(boolean isJar, String base, Path root, List<R> collector, Function<Resource, R> mapper)
            throws IOException {

    }

    String uriToString(URI uri) {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }

    String removeLeadingSlash(String str) {
        if (str.startsWith("/") || str.startsWith("\\")) {
            str = str.substring(1);
        }
        return str;
    }

    String removeTrailingSlash(String str) {
        if (str.endsWith("/") || str.endsWith("\\")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    Path jarUriToPath(URI jarUri, String rootPackagePathStr) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(rootPackagePathStr);
    }
}
