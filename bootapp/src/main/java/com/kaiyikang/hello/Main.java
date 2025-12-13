package com.kaiyikang.hello;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        // 向JVM了解，从哪里被加载出来，或是物理路径，或是压缩包的绝对路径
        String jarFile = Main.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        // 是否为打包
        boolean isJarFile = jarFile.endsWith(".war") || jarFile.endsWith(".jar");

        // IDE
        String webDir = "src/main/webapp";
        String baseDir = jarFile;

        // 默认使用当前的加载器
        ClassLoader appClassLoader = Main.class.getClassLoader();

        // JAR/WAR
        if (isJarFile) {
            webDir = "tmp-webapp-" + UUID.randomUUID().toString();
            baseDir = webDir;

            Path extractPath = Paths.get(webDir).normalize().toAbsolutePath();
            extract(jarFile, extractPath);

            List<URL> urls = new ArrayList<>();

            Path classesPath = extractPath.resolve("WEB-INF/classes");
            URL classesUrl = classesPath.toUri().toURL();
            urls.add(classesUrl);

            Path libDir = extractPath.resolve("WEB-INF/lib");
            if (Files.isDirectory(libDir)) {
                try (var stream = Files.list(libDir)) {
                    List<Path> jars = stream.filter(p -> p.toString().endsWith(".jar")).toList();
                    for (Path jar : jars) {
                        URL jarUrl = jar.toUri().toURL();
                        urls.add(jarUrl);
                    }
                }
            }
            appClassLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
            Thread.currentThread().setContextClassLoader(appClassLoader);

        }
        // WinterApplication.run(webDir, isJarFile ? "tmp-webapp" : jarFile,
        // HelloConfiguration.class);

        launchWinterByReflection(appClassLoader, webDir, baseDir, "com.kaiyikang.hello.HelloConfiguration", args);
    }

    private static void launchWinterByReflection(ClassLoader loader, String webDir, String baseDir,
            String configClassName, String[] args) throws Exception {
        System.out.println("正在通过反射启动 WinterApplication...");

        // 1. 使用我们需要的新加载器去加载类
        Class<?> winterClass = loader.loadClass("com.kaiyikang.winter.boot.WinterApplication");
        Class<?> configClass = loader.loadClass(configClassName);

        // 2. 找到 run 方法
        Method runMethod = winterClass.getMethod("run", String.class, String.class, Class.class, String[].class);

        // 3. 调用 run 方法
        runMethod.invoke(null, webDir, baseDir, configClass, args);
    }

    private static void extract(String jarFile, Path targetDir) throws IOException {
        if (Files.isDirectory(targetDir)) {
            deleteDir(targetDir);
        }
        Files.createDirectories(targetDir);
        System.out.println("正在解压 WAR 包到: " + targetDir);

        try (JarFile jar = new JarFile(jarFile)) {
            List<JarEntry> entries = jar.stream()
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .collect(Collectors.toList());
            for (JarEntry entry : entries) {
                Path res = targetDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(res.getParent());
                    Files.copy(jar.getInputStream(entry), res);
                }
            }
        }

        // 注册退出钩子，程序结束时删除临时目录
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDir(targetDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    private static void deleteDir(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
