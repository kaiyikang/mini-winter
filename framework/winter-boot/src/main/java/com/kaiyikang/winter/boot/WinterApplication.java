package com.kaiyikang.winter.boot;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.catalina.Context;
import org.apache.catalina.Server;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.utils.ClassPathUtils;
import com.kaiyikang.winter.web.ContextLoaderInitializer;
import com.kaiyikang.winter.web.utils.WebUtils;

public class WinterApplication {

    static final String CONFIG_APP_YAML = "/application.yml";
    static final String CONFIG_APP_PROP = "/application.properties";

    final Logger logger = LoggerFactory.getLogger(getClass());

    public static void run(String webDir, String baseDir, Class<?> configClass, String... args) throws Exception {
        // webDir 静态文件的根目录在哪里
        // baseDir - WebResourceRoot: 告诉Tomcat，/WEB-INF/classes对应的是baseDir路径，即编译后的
        // .class 路径
        new WinterApplication().start(webDir, baseDir, configClass, args);
    }

    public void start(String webDir, String baseDir, Class<?> configClass, String... args) throws Exception {
        printBanner();

        // Print start info
        final long startTime = System.currentTimeMillis();
        final int javaVersion = Runtime.version().feature();
        final long pid = ManagementFactory.getRuntimeMXBean().getPid();
        final String user = System.getProperty("user.name");
        final String pwd = Paths.get("").toAbsolutePath().toString();
        logger.info("Starting {} using Java {} with PID {} (started by {} in {})", configClass.getSimpleName(),
                javaVersion, pid, user, pwd);

        // Start the server
        var propertyResolver = WebUtils.createPropertyResolver();
        var server = startTomcat(webDir, baseDir, configClass, propertyResolver);

        // Print start info
        final long endTime = System.currentTimeMillis();
        final String appTime = String.format("%.3f", (endTime - startTime) / 1000.0);
        final String jvmTime = String.format("%.3f", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        logger.info("Started {} in {} seconds (process running for {})", configClass.getSimpleName(), appTime, jvmTime);

        server.await();
    }

    protected Server startTomcat(String webDir, String baseDir, Class<?> configClass, PropertyResolver propertyResolver)
            throws Exception {
        int port = propertyResolver.getProperty("${server.port:8080}", int.class);
        logger.info("starting Tomcat at port: {}...", port);
        Tomcat tomcat = new Tomcat();
        tomcat.getConnector().setThrowOnFailure(true);
        Context ctx = tomcat.addWebapp("baseDir", new File(webDir).getAbsolutePath());
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(
                new DirResourceSet(resources, "/WEB-INF/classes", new File(baseDir).getAbsolutePath(), "/"));
        ctx.setResources(resources);
        ctx.addServletContainerInitializer(new ContextLoaderInitializer(configClass, propertyResolver), Set.of());
        tomcat.start();
        logger.info("Tomcat started at port {}...", port);
        return tomcat.getServer();

    }

    protected void printBanner() {
        String banner = ClassPathUtils.readString("/banner.txt");
        banner.lines().forEach(System.out::println);
    }
}
