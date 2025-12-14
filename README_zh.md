# mini-winter

Mini Winter 来自于 Summer Framework 即一个基于 Java Spring Framework 的简化版本。

## 来源 (Source)

参考资料：

- [summer-framework](https://liaoxuefeng.com/books/summerframework/introduction/index.html)

## IOC - 控制反转 (Inversion Of Control)

### 加载类和文件 (Load Classes and Files)

在进入容器之前，我们需要先加载所有的类。ClassLoader 可以扫描所有以 `.class` 结尾的文件。
这是核心的代码：

```java
List<String> classList = resolver.scan(res -> {
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
```

其中 scan 会扫描所有的文件，我们需要将文件到类名的映射函数作为参数传进去。这里使用了函数式编程，类型是`Function<Resource, R>`。

在单元测试中，编译后的测试类位于测试类路径（classpath）下（例如 Maven 的：`target/test-classes/com/kaiyikang/scan/*`），而资源文件则会被复制到具有相同包结构的同一类路径根目录下（例如：`target/test-classes/com/kaiyikang/scan/sub1.txt`）。

### 属性解析器 (Property Resolver)

为了解析 `${app.title:default}` 类型的查询，我们可以采用递归的方法来处理高度复杂的情况，例如 `${app.path:${app.home:${ENV_NOT_EXIST:/not-exist}}}`。

首先，调用核心的 `public String getProperty(String key)` 方法。如果 `key` 能被 `PropertyExpr parsePropertyExpr(String key)` 持续解析并产生默认值，则意味着递归尚未结束。一旦递归完成，我们会使用类似的逻辑来解析生成的值，因为该值本身也可能是一个类似 `${app.title:default}` 的字符串，直到获得最终值。如果最终值为 null，则抛出错误；否则，返回该值。

具体的设计非常精妙。详情请参阅 `PropertyResolver.getProperty()`。

### Bean 定义 (BeanDefinition)

`BeanDefinition` 是一个核心类，旨在保存 Bean 的所有元数据，例如类名、作用域和生命周期回调。类似于一个模版，框架会根据该定义生成对应的实例。

Bean 主要通过两种方式定义：

1.  **基于类的 Bean (Class-based Beans)**：当一个类被标注了如 `@Component` 这样的构造型注解时，该类本身即作为 Bean 的定义。
2.  **工厂方法 Bean (Factory-method Beans)**：在 `@Configuration` 类中，标注了 `@Bean` 的方法充当创建 Bean 的工厂。

值得注意的是，`BeanDefinition` 中的 `beanClass` 字段存储的是 Bean 的**声明类型**（例如接口），而不是其实际的运行时类型（例如实现类）。这个声明类型对于依赖注入和类型查找至关重要，而实际类型只能在创建后通过 `instance.getClass()` 确定。

在开发 Bean 加载和初始化逻辑的过程中，我们非常强调健壮性。前置条件验证采用了**快速失败 (fail-fast)** 策略，以便尽早捕获配置错误，防止在运行时出现难以诊断的 `NullPointerExceptions`。

`AnnotationConfigApplicationContext` 的目的是扫描并收集所有带有有效注解的类，创建相应的 `BeanDefinition`，并将它们组织到一个以 Bean 名称为索引的内部注册表（Map）中。然后，它使用此注册表来定位并在请求时提供 Bean 实例。

### Bean 实例 (BeanInstance)

**强依赖 (Strong dependencies)**，即通过构造函数或工厂方法注入的依赖，不能存在循环引用。如果发现此类依赖循环，容器必须抛出错误。相比之下，**弱依赖 (Weak dependencies)** 允许通过将实例化与属性注入分离来支持循环引用。

这导致了一个两阶段的 Bean 创建过程：

1.  **实例化 (Instantiation)：** 通过调用构造函数创建 Bean 实例，解决所有强依赖。
2.  **填充 (Population)：** 随后通过 Setter 和字段注入，将弱依赖填充到实例中。

关于 API 命名约定：`get` 方法预期总是返回一个对象（或抛出异常），而 `find` 方法如果未找到对象则可能返回 `null`。参见 `getBean` 和 `findBean` 的实际示例。

关于弱依赖的实现细节，可以参考 `tryInjectProperties` 方法。它接收目标 `bean` 实例和一个 `acc` 对象（代表待注入的字段或方法）。该过程包括通过 `setAccessible(true)` 使成员可访问，解析需要注入的依赖 Bean，最后使用反射设置字段的值或使用解析出的依赖调用 Setter 方法。

### Bean 后置处理器 (BeanPostProcessor)

当前的流程如下：

1.  创建 Bean 定义。
2.  根据定义创建实例。
3.  将其他实例注入到该实例中。
4.  完整的 Bean 准备就绪。

现在出现了一个新需求：如果我们需要替换一个 Bean 或向已实例化的 Bean 添加新逻辑，因此我们需要使用 `BeanPostProcessor`，而这发生在步骤 2 和 3 。

在步骤 2 之后，可以创建一个 `BeanProxy` 并将其返回给工厂，以替换原始 Bean。对于步骤 3，假设 BeanA 依赖 BeanB（即 BeanA 是主体容器，BeanB 是依赖项），或说 BeanB 注入 BeanA 中。此时：

1. 接受注入的 BeanA 需要是原始的，因为它是具体干活的。
2. 被注入的 BeanB 是代理的，因为 BeanA 调用 BeanB 的时候，属于 B 的逻辑应该被正确触发。

为了解决第二个问题，我们可以扩展 `BeanPostProcessor` 并添加一个新方法。该方法确保在向 Bean 注入属性时，返回**原始 Bean**用于注入过程。此外，在使用这些属性时，我们应**始终使用 getter** 而不是直接访问字段，因为注入是发生在原始 Bean 上的。

在单元测试中，我们可以观察到代理的引用链：

```bash
proxy: SecondProxyBean@23
name = null
target = FirstProxyBean@96
    name = null
    target = OriginBean@98
        name = "Scan App"
        version = "v1.0"
    version = null
version = null
```

### 完成 IOC (Finish IOC)

完成接口部分。

## AOP - 面向切面编程 (Aspect-Oriented Programming)

**面向切面编程 (AOP)** 为已组装的 Bean 对象添加额外的通用功能。这些功能的特点是不属于核心业务逻辑，但它横切了不同业务领域的方法。AOP 为 Bean 添加了一个代理层。当调用 Bean 的方法时，首先调用代理的方法，代理再让 Bean 处理请求，最后可以添加一些收尾工作。

有三种实现方式：

- **编译时 (Compile-time)**：“附加”代码在编译期间直接织入 `.class` 文件。性能最好但最复杂，需要特殊的编译器。
- **加载时 (Load-time)**：当 `.class` 文件加载到 JVM 内存时被拦截，并注入“附加”逻辑。这种方式很灵活，但需要了解 JVM 的类加载机制。
- **运行时 (Run-time)**：“附加”代码是一个普通的 Java 类，作为 Bean 的代理对象动态生成。这是最常见也是最简单的方法。

mini-winter 仅支持基于注解的方式，即 Bean 清楚地知道它拥有什么“附加”功能。它支持代理所有类型的类，并使用 Byte Buddy 作为其实现机制。

### 代理解析器 (ProxyResolver)

两件事至关重要：

1.  原始 Bean。
2.  拦截器 (Interceptor)：它拦截目标 Bean 的方法调用，并自动调用拦截器以实现代理功能。

执行流程如下：

1.  调用代理的方法。
2.  代理将调用转发给拦截器。
3.  拦截器执行其额外逻辑，并决定何时调用原始 Bean 的方法。
4.  执行原始 Bean 的方法。
5.  拦截器处理结果。
6.  代理返回最终结果。

AOP 淡化了像方法和字段这样的具体概念，转而强调像“方法调用”这样的动态概念。在 AOP 术语中，目标对象上的方法调用或字段访问等事件点被定义为**连接点 (Join Points)**。我们感兴趣并筛选出来的特定连接点被定义为**切入点 (Pointcut)**。

在切入点要做的事情称为**通知/增强 (Advice)**。

**切面 (Aspect)** 是**切入点**和**通知**的结合。它通过定义“在哪里”（切入点）和“做什么”（通知），模块化了横切多个类型和对象的关注点。

### 环绕 (Around)

`@Polite` 注解应用于方法，表示它们需要特殊处理。对于需要应用 AOP 的类，使用 `@Around("aroundInvocationHandler")`，其中的值指定框架应使用哪个处理器来处理该切面。

`AroundInvocationHandler` 是一个标记为 `@Component` 的常规 Bean。要将处理器装配到应用程序中，需使用 `@Configuration` 和 `@ComponentScan`，并用 `@Bean` 标注 `AroundProxyBeanPostProcessor` 对象的创建，以便容器管理它。随后，`AroundProxyBeanPostProcessor` 扫描 Bean 类以检查它们是否包含 `@Around` 注解。如果存在，它为原始 Bean 创建并返回一个代理；否则，原样返回 Bean。

此外，为了实现 **before (前置)** 或 **after (后置)** 模式，可以通过提供 `BeforeInvocationHandlerAdapter` 和 `AfterInvocationHandlerAdapter` 来使用适配器模式。

最后，要使用自定义注解（例如 `@Transactional`）实现 AOP，可以使用泛型基类 `AnnotationProxyBeanPostProcessor<A extends Annotation>`。只需创建一个继承自 `AnnotationProxyBeanPostProcessor<Transactional>` 的类即可实现。

## JDBC - Java 数据库连接与事务

**事务 (Transaction)** 是数据库操作中的一个基本概念，它保证了 **ACID** 属性：

- **原子性 (Atomicity)**：事务内的所有操作要么全部成功，要么全部失败并回滚。这是一个“全有或全无”的原则。
- **一致性 (Consistency)**：事务完成后，数据库保持一致状态。它从一个有效状态转换到另一个有效状态。
- **隔离性 (Isolation)**：并发事务互不影响，一个事务的中间状态对其他事务不可见。
- **持久性 (Durability)**：一旦事务提交，其对数据库的更改就是永久的，即使系统发生故障也能保留。

本章将涵盖**声明式事务**的实现，主要是通过 `@Transactional` 注解。当执行标记有此注解的方法时，底层框架会自动管理事务生命周期（开始、提交、回滚）以强制执行这些 ACID 属性。

### JdbcTemplate

**JDBC** 代表 **Java Database Connectivity**，它是 Java 应用程序连接数据库的标准 API。在本章中，我们将实现一个 `JdbcTemplate`。该模板封装了原始 JDBC 的冗长和样板代码，允许开发人员专注于编写 SQL 和提供参数。

我们将使用 **HikariCP**（一个高性能连接池）来提供 `javax.sql.DataSource` 接口的实现。然后，可以将此 `DataSource` 注册为 IoC 容器中的 bean。`JdbcTemplate` 将使用此 `DataSource` 执行数据库操作。

为了连接数据库并执行查询，JDBC 定义了一个标准的、分层的对象创建流程。该过程确保了结构化和安全的数据库访问。

核心关系由以下伪代码演示：

```java
String sql = "SELECT ... FROM ... WHERE ...";

// 1. 从 DataSource 获取 Connection
try (Connection con = dataSource.getConnection();

     // 2. 使用 Connection 创建 PreparedStatement
     PreparedStatement ps = con.prepareStatement(sql)) {

    // 3. 执行 PreparedStatement 以获取 ResultSet
    try (ResultSet rs = ps.executeQuery()) {

        // 4. 遍历 ResultSet
        while (rs.next()) {
            // 从当前行提取数据
        }
    }
}
```

这说明了一个严格的依赖链：`DataSource` 创建 `Connection`，后者依次创建 `PreparedStatement`，其执行产生 `ResultSet`。所有这些（`Connection`, `PreparedStatement`, `ResultSet`）都是使用后必须关闭的资源。管理它们的推荐方法是使用 `try-with-resources` 语句。

在我们的实现中，这种资源管理通过一个 `execute` 函数优雅地处理，该函数采用**借贷模式 (Loan Pattern)**（也称为**环绕执行方法模式**）。该模式的工作原理如下：方法获取资源（例如 `Connection`），将其“借”给代码块（通常是 Lambda 表达式）使用，最后确保资源被安全释放，无论代码是成功执行还是抛出异常。

### `@Transactional` 注解

`@Transactional` 注解通过利用事务生命周期，有效地确保了操作的**原子性**。

`DataSourceTransactionManager` 类在通过 AOP 机制（由自定义的 `AnnotationProxyBeanPostProcessor<Transactional>` 编排）调用时，利用 `ThreadLocal` 来管理事务。

此 `ThreadLocal` 用于将当前事务绑定到执行线程。

- **如果当前线程已绑定事务**（即 `ThreadLocal` 包含活动事务），`DataSourceTransactionManager` 通常会在同一线程内的后续方法调用中**加入此现有事务**。
- **否则**，如果当前未绑定事务，它将**启动一个新事务**。

这种加入现有事务的场景在**嵌套方法调用**中特别相关，即外部方法已经启动了一个事务。

因此，像 `TransactionalUtils.getCurrentConnection()` 这样的实用方法可以检索当前绑定到当前线程活动事务的数据库连接。

## MVC 的实现 (Implementation of MVC)

### Boot WebApp

在本节中，我们的目标是使用当前的 mini-winter 框架创建一个 WebApp，并将其打包为 WAR (Web Application Archive) 文件。当 Tomcat 服务器启动时，它将扫描并加载此 WAR 文件，确保在浏览器中访问相应的 URL 时，能正确显示 WebApp 的响应。

典型的 Java Web 应用程序遵循 Servlet 规范。Servlet 规范不仅定义了 WebApp 必须实现的接口，还定义了 Web 服务器（如 Tomcat）应如何加载 WebApp、请求的处理顺序以及各种组件的调用方式。这建立了一个清晰的解耦模型。Servlet 规范定义了三种核心组件类型：

1.  **Listener (监听器)**：监听 WebApp 的生命周期事件，例如应用程序启动和关闭，以及 Session 创建和属性更改。
2.  **Filter (过滤器)**：在 HTTP 请求到达最终 Servlet 之前执行。它用于授权、限流、日志记录和缓存验证等任务。
3.  **Servlet**：最终处理 HTTP 请求，确定针对 GET、POST 或其他方法执行的业务逻辑，并生成响应。

单个 Tomcat 实例可以部署多个 WebApp。每个 WebApp 都有自己的 `ServletContext`，通常称为“Web 应用程序上下文”。属于同一 WebApp 的所有 Servlet、Filter、Listener 和资源文件都在其自己隔离的 `ServletContext` 中运行。

我们的 mini-winter WebApp 也将完全运行在 Tomcat 为其创建的 `ServletContext` 中。

当 Tomcat 启动时，它会扫描 `webapps` 目录，将每个 WAR 文件视为一个 Web 应用程序，并为其创建一个相应的 `ServletContext`。然后 Tomcat 读取 `WEB-INF/web.xml` 文件，其中包含：

```xml
<listener>
    <listener-class>com.kaiyikang.winter.web.ContextLoaderListener</listener-class>
</listener>
```

根据此配置，Tomcat 加载 `ContextLoaderListener` 并调用其 `contextInitialized()` 方法。在这个类中，我们遵守 `ServletContextListener` 规范，通过重写 `contextInitialized()` 来定义 WebApp 启动时应运行的初始化逻辑。

对于 mini-winter 框架，两个最关键的任务是：

1.  **创建 `ApplicationContext`**：这是 mini-winter 的 IoC 容器，负责组件扫描、Bean 实例化和依赖管理。
2.  **注册 `DispatcherServlet` 并将其映射到根路径 `/`**：在其初始化期间，`DispatcherServlet` 获取对 `ApplicationContext`（在第一步中创建）的引用，这使其能够访问所有扫描到的控制器和服务组件。

因此，一旦 WebApp 成功启动，`DispatcherServlet` 就充当整个应用程序的“前端控制器 (Front Controller)”。当任何用户向此 WebApp 发送 HTTP 请求时，Tomcat 的 URL 匹配规则会将请求委托给 `DispatcherServlet` 进行处理。由于它映射到 `/`，几乎所有路径（除了少数例外）都将路由到它。

`DispatcherServlet` 已经持有对 `ApplicationContext` 的完整引用。因此，在处理请求时，它可以直接访问 mini-winter 框架内的组件，如 Controller 和 Service，而不依赖于 Servlet API 的传统分发机制。请求路由和随后的业务逻辑完全由 mini-winter 框架的内部机制处理。

### MVC 的实现 (Implement of MVC)

在本节中，我们将完善 `DispatcherServlet`，首先定义诸如 `@Controller` 和 `@RestController` 之类的注解来标记类，然后创建像 `@GetMapping` 这样的注解来装饰方法。这些方法绑定到特定的端点路径，如以下示例所示：

```java
@GetMapping("/hello/{name}")
    @ResponseBody
    String hello(@PathVariable("name") String name) {
        return "Hello, " + name;
    }
```

因此，`DispatcherServlet` 负责扫描所有标注了 `@Controller` 或 `@RestController` 的类，同时定义作为特定 URL 处理器的“分发器 (dispatchers)”。我们还定义了这些方法的参数，并将它们分为四种不同的类型，包括从 URL 中提取路径参数的 `PATH_VARIABLE`，从查询字符串或表单数据中提取参数的 `REQUEST_PARAM`，从 POST 请求的 JSON 负载中提取数据的 `REQUEST_BODY`，以及通过 `HttpServletRequest` API 检索标准对象的 `SERVLET_VARIABLE`。

我们利用反射来检查方法并注册这些分发器实例，如下所示：

```java
PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                checkMethod(m);
                this.postDispatchers.add(new Dispatcher("POST", isRest, instance, m, post.value()));
            }
```

随后，我们可以在 `doGet` 方法中调用逻辑，因为它重写了标准的 `HttpServlet` 实现。该方法区分了处理静态资源文件和处理常规服务请求：

```java
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String url = req.getRequestURI();
        if (url.equals(this.faviconPath) || url.startsWith(this.resourcePath)) {
            doResource(url, req, resp);
        } else {
            doService(req, resp, getDispatchers);
        }
    }
```

最后，在 `handleRestResult` 和 `handleMvcResult` 中，我们根据具体的类型处理分发器的返回值。`void` 返回类型表示请求已在内部处理，而 `String` 可能代表视图名称或触发重定向（如果它以 "redirect:" 开头）。此外，带有 `@ResponseBody` 的 `String` 或 `byte[]` 意味着内容直接写入响应，而 `ModelAndView` 对象表示包含模型和视图数据的 MVC 响应，需要由 `FreeMarkerViewResolver` 进行渲染。

### 创建 WebApp (Create WebApp)

首先，在 src/main/resources 目录中创建 application.yml 文件：

```yaml
app:
  title: Hello Application
  version: 1.0

winter:
  datasource:
    url: jdbc:sqlite:test.db
    driver-class-name: org.sqlite.JDBC
    username: sa
    password:
```

接下来，您将创建核心组件，其中包括 HelloConfiguration 类以及相关的 Service 和 Controller 类，这些类将被注册为 Mini Winter 框架内的 Bean。Service 层将负责与 User 数据库相关的操作。而 Web 层将包含 Controller 和 Filter 组件。

此外，您仍然需要创建静态文件，而且至关重要的是，通过必要的 web.xml 文件为 Web 容器（如 Tomcat）提供配置。

最后，要部署应用程序，您只需要将生成的 WAR 文件放入 Tomcat 的 webapps 目录中。服务器运行后，您可以通过在浏览器中导航到 `localhost:8080` 来查看 Web 应用程序。

### Winter Boot

在上一章中，我们成功实现了一个 Web 应用程序。然而，在传统的开发和部署场景中，工作流程涉及打包应用程序、复制文件以及手动启动外部 Tomcat 服务器，这可能相当复杂。为了简化此过程，我们可以将 Web 应用程序直接与 Winter 框架集成，以创建最终的“Winter Boot”。这消除了安装外部 Tomcat 或复制 WAR 文件的需要；应用程序可以直接从单个 JAR 文件运行。

### 启动嵌入式 Tomcat (Start the embedded Tomcat)

当程序执行 `WinterApplication.run()` 时，它会启动**嵌入式 Tomcat**。

由于我们绕过了传统的 `web.xml` 配置，我们必须使用 `addWebapp` 和 `WebResourceRoot` 手动**挂载文件系统**，并将虚拟路径映射到我们编译后的类文件的物理位置。

随后，我们注册一个 `ServletContainerInitializer`。Tomcat 在其启动阶段会触发此初始化器。这个钩子负责关键的引导过程：创建 `AnnotationConfigApplicationContext`（初始化 IoC 容器）并调用 `WebUtils.registerDispatcherServlet` 来注册 DispatcherServlet。

这里是您总结的润色后的中文翻译。我确保了诸如“父类委托模型”等技术术语以及“僵尸目录”问题背后的逻辑得到了清晰的表达。

### 实现 Boot 应用 (Implementing the Boot App)

在我们的代码中，我们使用 `jarFile` 路径来判断应用程序是在 IDE 中运行还是作为打包的构件通过 `java -jar` 运行。因此，`webDir`（静态资源路径）和 `baseDir`（编译后的 Java 文件路径）会相应地进行调整。

如果我们严格按照原始教程操作，代码在 IDE 中可以正确运行。但是，通过 `java -jar ./target/hello-boot.war` 执行打包后的构件会失败，并出现 `NoClassDefFoundError`，例如：
`Exception in thread "main" java.lang.NoClassDefFoundError: com/kaiyikang/winter/boot/WinterApplication`

这个错误源于 JVM 的启动过程。在使用 `mvn clean package` 打包源代码并运行 WAR 文件后：

1.  JVM 读取 `MANIFEST.MF` 文件，其中包含：
    - `Main-Class: com.kaiyikang.hello.Main`
    - `Class-Path: tmp-webapp/WEB-INF/lib/...`
2.  在这个精确时刻（启动时），`tmp-webapp` 目录**尚不存在**。
3.  因此，JVM 会使这些路径失效（将它们从内部类路径列表中移除），并仅从 WAR 中加载 `Main.class`。
4.  虽然 `Main.java` 执行并成功提取文件创建了 `tmp-webapp` 目录，但为时已晚。当代码尝试调用 `WinterApplication.run` 时，JVM 会失败，因为该类不在初始的类路径查找中，导致 `NoClassDefFoundError`。

为了解决这个问题，我们必须在 WAR 提取之后（即 `tmp-webapp` 创建之后）手动创建一个新的 ClassLoader，并使用它来加载 `WinterApplication`。

我们通过使用 `new URLClassLoader` 实例化一个自定义的 `appClassLoader` 来实现这一点。但是，有几个关键的陷阱需要解决：

1.  **Tomcat 配置：** 嵌入式 Tomcat 可能不会自动识别此自定义 ClassLoader，并可能默认使用系统加载器。因此，在 `WinterApplication` 内部，我们必须显式设置 Tomcat 的父类加载器：
    `ctx.setParentClassLoader(Thread.currentThread().getContextClassLoader());`

2.  **“僵尸目录”问题：** 这是最关键的问题。“僵尸目录”指的是从以前的运行中残留的 `tmp-webapp` 文件夹。在启动期间，JVM 检测到此现有文件夹并自动将其添加到 `AppClassLoader` 的搜索路径中。如果我们的自定义 ClassLoader 默认使用 `AppClassLoader` 作为其父级，Java 的**父类委托模型 (Parent Delegation Model)** 规定父级会尝试首先加载类。`AppClassLoader` 将急切地从“僵尸”目录（旧版本）加载类，导致版本不匹配或依赖项丢失错误。

我们通过实现 ClassLoader 隔离来解决这个问题。在创建 `URLClassLoader` 时，我们显式将其父级设置为 **`PlatformClassLoader`**（它是 `AppClassLoader` 的父级，仅负责 JDK 扩展库）。

这有效地切断了对 `AppClassLoader` 的委托，绕过了僵尸目录的干扰，并强制我们的自定义加载器加载新提取的 Jar 包。

## 思考 (Thinking)

1.  在编写类或方法之前先阅读它，思考它的功能以及它是如何编写的。
2.  从单元测试开始会更容易理解。

## 时间线 (Timeline)

2025.09.05 资源解析器 (ResourceResolver) 完成
2025.09.09 属性解析器 (PropertyResolver) 完成
2025.09.17 Bean 定义 (BeanDefinition) 完成
2025.09.19 创建 Bean 实例 (Create BeanInstance) 完成
2025.09.24 Bean 后置处理器 (BeanPostProcessor) 完成
2025.09.24 IOC 完成
2025.09.26 代理解析器 (ProxyResolver) 完成
2025.10.17 环绕 (Around) 完成
2025.11.03 JdbcTemplate 完成
2025.11.04 事务 (Transactional) 完成
2025.11.23 Boot WebApp 完成
2025.12.10 MVC 的实现 (Implement of MVC) 完成
2025.12.10 创建 WebApp (Create WebApp) 完成
2025.12.10 启动嵌入式 Tomcat (Start the embedded Tomcat) 完成
2025.12.13 实现 Boot 应用 (Implementing the Boot App) 完成
