package com.kaiyikang.hello;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import com.kaiyikang.winter.boot.WinterApplication;

public class Main {
    public static void main(String[] args) throws Exception {
        // 向JVM了解，从哪里被加载出来，或是物理路径，或是压缩包的绝对路径
        String jarFile = Main.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        // 是否为打包
        boolean isJarFile = jarFile.endsWith(".war") || jarFile.endsWith(".jar");

        // 找到 webapp的根目录: 生产模式/开发模式
        String webDir = isJarFile ? "tmp-webapp" : "src/main/webapp";

        if (isJarFile) {

            Path baseDir = Paths.get(webDir).normalize().toAbsolutePath();
            if (Files.isDirectory(baseDir)) {
                Files.delete(baseDir);
            }

            Files.createDirectories(baseDir);
            System.out.println("extract to: " + baseDir);
            try (JarFile jar = new JarFile(jarFile)) {
                List<JarEntry> entries = jar.stream().sorted(Comparator.comparing(JarEntry::getName))
                        .collect(Collectors.toList());
                for (JarEntry entry : entries) {
                    Path res = baseDir.resolve(entry.getName());
                    if (!entry.isDirectory()) {
                        System.out.println(res);
                        Files.createDirectories(res.getParent());
                        Files.copy(jar.getInputStream(entry), res);
                    }
                }
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walk(baseDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        }
        WinterApplication.run(webDir, isJarFile ? "tmp-webapp" : jarFile, HelloConfiguration.class);
    }
}
