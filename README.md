# MINI-WINTER

<p align="center">
  <img src="./resources/mini-winter-cover.jpg" width="500" title="Mini Winter Framework">
</p>

Mini Winter comes from Summer Framework, which is a simplified version based on Java Spring Framework.

## Source

For reference:

- [summer-framework](https://liaoxuefeng.com/books/summerframework/introduction/index.html)

## 1. IOC - Inversion Of Control

### 1.1. Load Classes and Files

We need to load all the classes first before we can go into the container.

In unit tests, compiled test classes end up under the test classpath (e.g., Maven: target/test-classes/com/kaiyikang/scan/\*), and resources are copied to the same classpath root with the same package layout (e.g., target/test-classes/com/kaiyikang/scan/sub1.txt).

### 1.2. Property Resolver

To parse queries of the type `${app.title:default}`, a recursive approach can be adopted to handle highly complex situations, such as `${app.path:${app.home:${ENV_NOT_EXIST:/not-exist}}}`.

First, the core `public String getProperty(String key)` method is called. If the `key` can be continuously parsed by `PropertyExpr parsePropertyExpr(String key)` to yield a default value, it means the recursion has not ended. Once the recursion is complete, a similar logic is used to resolve the resulting value, as the value itself could also be a string like `${app.title:default}`, until a final value is reached. If the final value is null, an error is thrown; otherwise, the value is returned.

The specific design is quite ingenious. For details, please refer to `PropertyResolver.getProperty()`.

### 1.3. BeanDefinition

A `BeanDefinition` is a core class designed to hold all metadata for a Bean, such as its class name, scope, and lifecycle callbacks.

Beans are primarily defined in two ways:

1. **Class-based Beans**: When a class is annotated with a stereotype like `@Component`, the class itself serves as the Bean's definition.
2. **Factory-method Beans**: In a `@Configuration` class, methods annotated with `@Bean` act as factories to create Beans.

Notably, the `beanClass` field in a `BeanDefinition` stores the Bean's **declared type** (e.g., an interface), not its actual runtime type (e.g., the implementation class). This declared type is crucial for dependency injection and type lookups, while the actual type can only be determined via `instance.getClass()` after creation.

During the development of the Bean loading and initialization logic, a strong emphasis was placed on robustness. The pre-condition validation was enhanced with a **fail-fast** strategy to catch configuration errors early, preventing hard-to-diagnose `NullPointerExceptions` at runtime.

The purpose of the `AnnotationConfigApplicationContext` is to scan for and collect all classes with valid annotations, create corresponding `BeanDefinition`s, and organize them into an internal registry (a Map) indexed by bean name. It then uses this registry to locate and serve Bean instances upon request.

### 1.4. BeanInstance

**Strong dependencies**, those supplied through constructor or factory method injection, cannot have circular references. The container must throw an error if such a dependency loop is found. In contrast, **weak dependencies** allow for circular references by separating instantiation from property injection.

This leads to a two-phase bean creation process:

1. **Instantiation:** The bean instance is created by invoking its constructor, which resolves all strong dependencies.
2. **Population:** The instance is then populated with weak dependencies through setter and field injection.

Regarding API naming conventions: `get` methods are expected to always return an object (or throw an exception), whereas `find` methods may return `null` if the object is not found. See `getBean` and `findBean` for a practical example.

For an implementation detail on weak dependencies, consider the `tryInjectProperties` method. It takes the target `bean` instance and an `acc` object (representing the field or method to be injected). The process involves making the member accessible via `setAccessible(true)`, resolving the dependency bean that needs to be injected, and finally, using reflection to set the field's value or invoke the setter method with the resolved dependency.

### 1.5. BeanPostProcessor

The current process is as follows:

1. Create the bean definition.
2. Create an instance based on the definition.
3. Inject other instances into this instance.
4. The complete bean is ready.

A new requirement has emerged: if we need to replace a bean or add new logic to an already instantiated bean, we must use a `BeanPostProcessor`. This happens around steps 2 and 3.

After step 2, a `BeanProxy` can be created and returned to the factory, replacing the original bean. For step 3, assume BeanA depends on BeanB (i.e., BeanA is the main container, and BeanB is the dependency), or in other words, BeanB is injected into BeanA. In this case:

1. The BeanA receiving the injection needs to be the original one, because it is the one doing the actual work.
2. The BeanB being injected must be the proxy, because when BeanA calls BeanB, the logic belonging to B should be triggered correctly.

To solve the second problem, we can extend `BeanPostProcessor` with a new method. This method ensures that when injecting properties into a bean, the **original bean** is returned for the injection process. Furthermore, when using these properties, we should **always use getters** instead of accessing fields directly, because the injection occurs on the original bean.

In the unit test, we can observe the proxy's reference chain:

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

### 1.6. Finish IOC

Finish interface.

## 2. AOP - Aspect-Oriented Programming

**Aspect-Oriented Programming (AOP)** adds extra, common functionality to already assembled Bean objects. This functionality is characterized by not being part of the core business logic, yet it cuts across methods in different business areas. AOP adds a proxy layer to a Bean. When a bean's method is called, the proxy's method is invoked first, which then lets the bean handle the request, and finally, some finishing work can be added.

There are three ways to implement this:

- **Compile-time**: The "add-on" code is directly woven into the `.class` file during compilation. It offers the best performance but is the most complex, requiring a special compiler.
- **Load-time**: When a `.class` file is being loaded into the JVM's memory, it is intercepted, and the "add-on" logic is injected. This is flexible but requires an understanding of the JVM's class-loading mechanism.
- **Run-time**: The "add-on" code is a regular Java class, which is dynamically generated as a proxy object for the Bean. This is the most common and simplest approach.

mini-winter only supports the annotation-based approach, where a Bean clearly knows what "add-on" it has. It supports proxying all types of classes and uses Byte Buddy as its implementation mechanism.

### 2.1. ProxyResolver

Two things are essential:

1. The original Bean.
2. The Interceptor: It intercepts the target Bean's method calls and automatically invokes the interceptor to implement the proxy functionality.

The execution flow is as follows:

1. The proxy's method is called.
2. The proxy forwards the call to the interceptor.
3. The interceptor executes its extra logic and decides when to call the original Bean's method.
4. The original Bean's method is executed.
5. The interceptor processes the result.
6. The proxy returns the final result.

AOP de-emphasizes concrete concepts like methods and fields, instead emphasizing dynamic concepts like "method invocation." In AOP terminology, event points like method calls or field access on a target object are defined as **Join Points**. The specific Join Points that we are interested in, which are filtered out, are defined by a **Pointcut**.

What to do at a Pointcut is called an **Advice**.

An **Aspect** is the combination of a **Pointcut** and an **Advice**. It modularizes a concern that cuts across multiple types and objects by defining "where" (the Pointcut) and "what" (the Advice) to do.

### 2.2. Around

The `@Polite` annotation is applied to methods to indicate that they require special processing. For classes that need AOP applied, use `@Around("aroundInvocationHandler")`, where the value specifies which handler the framework should use to process the aspect.

The `AroundInvocationHandler` is a regular Bean marked with `@Component`. To wire the handler into the application, use `@Configuration` and `@ComponentScan`, and annotate the creation of the `AroundProxyBeanPostProcessor` object with `@Bean` to let the container manage it. Subsequently, `AroundProxyBeanPostProcessor` scans Bean classes to check if they contain the `@Around` annotation. If present, it creates and returns a proxy for the original Bean; otherwise, it returns the Bean unmodified.

Additionally, to implement **before** or **after** patterns, the adapter pattern can be used by providing `BeforeInvocationHandlerAdapter` and `AfterInvocationHandlerAdapter`.

Finally, to implement AOP using custom annotations (e.g., `@Transactional`), you can use the generic base class `AnnotationProxyBeanPostProcessor<A extends Annotation>`. Simply create a class that extends `AnnotationProxyBeanPostProcessor<Transactional>` to achieve this.

## 3. JDBC - Java DataBase Connectivity & Transactions

A **transaction** is a fundamental concept in database operations that guarantees **ACID** properties:

- **Atomicity**: All operations within the transaction either succeed entirely, or they all fail and are rolled back. It's an "all-or-nothing" principle.
- **Consistency**: The database remains in a consistent state after the transaction is completed. It transitions from one valid state to another.
- **Isolation**: Concurrent transactions do not affect each other, and the intermediate state of one transaction is not visible to others.
- **Durability**: Once a transaction is committed, its changes to the database are permanent and will survive system failures.

This chapter will cover the implementation of **declarative transactions**, notably through the `@Transactional` annotation. When a method marked with this annotation is executed, the underlying framework automatically manages the transaction lifecycle (begin, commit, rollback) to enforce these ACID properties.

### 3.1. JdbcTemplate

**JDBC** stands for **Java Database Connectivity**, which is the standard API for connecting Java applications to databases. In this chapter, we will implement a `JdbcTemplate`. This template encapsulates the verbose and boilerplate code of raw JDBC, allowing developers to focus on writing SQL and providing parameters.

We will use **HikariCP**, a high-performance connection pool, to provide an implementation of the `javax.sql.DataSource` interface. This `DataSource` can then be registered as a bean in the IoC container. The `JdbcTemplate` will use this `DataSource` to perform database operations.

To connect to a database and execute a query, JDBC defines a standard, hierarchical object creation flow. This process ensures structured and safe database access.

The core relationship is demonstrated by the following pseudo-code:

```java
String sql = "SELECT ... FROM ... WHERE ...";

// 1. Get a Connection from the DataSource
try (Connection con = dataSource.getConnection();

     // 2. Create a PreparedStatement using the Connection
     PreparedStatement ps = con.prepareStatement(sql)) {

    // 3. Execute the PreparedStatement to get a ResultSet
    try (ResultSet rs = ps.executeQuery()) {

        // 4. Iterate through the ResultSet
        while (rs.next()) {
            // Extract data from the current row
        }
    }
}
```

This illustrates a strict dependency chain: a `DataSource` creates a `Connection`, which in turn creates a `PreparedStatement`, and its execution yields a `ResultSet`. All of these (`Connection`, `PreparedStatement`, `ResultSet`) are resources that must be closed after use. The recommended way to manage them is with a `try-with-resources` statement.

In our implementation, this resource management is handled elegantly by an `execute` function that employs the **Loan Pattern** (also known as the **Execute Around Method Pattern**). This pattern works as follows: a method acquires a resource (e.g., a `Connection`), "loans" it to a block of code (typically a lambda expression) for use, and finally ensures the resource is safely released, regardless of whether the code executes successfully or throws an exception.

### 3.2. The `@Transactional` Annotation

The `@Transactional` annotation, by leveraging a transaction lifecycle, effectively ensures the **atomicity** of operations.

The `DataSourceTransactionManager` class, when invoked via an AOP mechanism (as orchestrated by your custom `AnnotationProxyBeanPostProcessor<Transactional>`), manages transactions by utilizing a `ThreadLocal`.

This `ThreadLocal` is used to bind the current transaction to the executing thread.

- **If a transaction is already bound to the current thread** (i.e., the `ThreadLocal` contains an active transaction), the `DataSourceTransactionManager` will typically **join this existing transaction** for subsequent method invocations within the same thread.
- **Otherwise**, if no transaction is currently bound, it will **initiate a new transaction**.

This scenario of joining an existing transaction is particularly relevant in **nested method calls** where an outer method has already started a transaction.

Consequently, utility methods like `TransactionalUtils.getCurrentConnection()` can retrieve the database connection that is currently bound to the active transaction on the current thread.

## 4. Implementation of MVC

### 4.1. Boot WebApp

In this section, our goal is to create a WebApp using the current mini-winter framework and package it as a WAR (Web Application Archive) file. When the Tomcat server starts, it will scan for and load this WAR file, ensuring that when the corresponding URL is accessed in a browser, the WebApp's response is correctly displayed.

A typical Java web application adheres to the Servlet Specification. The Servlet Specification defines not only the interfaces that a WebApp must implement but also how a web server (like Tomcat) should load the WebApp, the order in which requests are processed, and how various components are invoked. This establishes a clear decoupling model. The Servlet Specification defines three core component types:

1. **Listener**: Listens for lifecycle events of the WebApp, such as application startup and shutdown, as well as Session creation and attribute changes.
2. **Filter**: Executes before an HTTP request reaches the final Servlet. It is used for tasks like authorization, rate limiting, logging, and cache validation.
3. **Servlet**: Handles the HTTP request, determining the business logic to execute for GET, POST, or other methods, and generating the response.

A single Tomcat instance can deploy multiple WebApps. Each WebApp has its own `ServletContext`, often referred to as the "web application context." All Servlets, Filters, Listeners, and resource files belonging to the same WebApp operate within their own isolated `ServletContext`.

Our mini-winter WebApp will also run entirely within the `ServletContext` that Tomcat creates for it.

When Tomcat starts up, it scans the `webapps` directory, treating each WAR file as a web application and creating a corresponding `ServletContext` for it. Tomcat then reads the `WEB-INF/web.xml` file, which contains:

```xml
<listener>
    <listener-class>com.kaiyikang.winter.web.ContextLoaderListener</listener-class>
</listener>
```

Based on this configuration, Tomcat loads the `ContextLoaderListener` and invokes its `contextInitialized()` method. In this class, we adhere to the `ServletContextListener` specification by overriding `contextInitialized()` to define the initialization logic that should run when the WebApp starts.

For the mini-winter framework, the two most critical tasks are:

1. **Create the `ApplicationContext`**: This is the IoC (Inversion of Control) container for mini-winter, responsible for component scanning, bean instantiation, and dependency management.
2. **Register the `DispatcherServlet` and map it to the root path `/`**: During its initialization, the `DispatcherServlet` obtains a reference to the `ApplicationContext` (created in the first step), which allows it to access all scanned controllers and service components.

Thus, once the WebApp has started successfully, the `DispatcherServlet` acts as the "Front Controller" for the entire application. When any user sends an HTTP request to this WebApp, Tomcat's URL matching rules will delegate the request to the `DispatcherServlet` for processing. Since it is mapped to `/`, virtually all paths (with a few exceptions) will be routed to it.

The `DispatcherServlet` already holds a complete reference to the `ApplicationContext`. Therefore, when processing a request, it can directly access components within the mini-winter framework, such as Controllers and Services, without relying on the traditional dispatching mechanisms of the Servlet API. The request routing and subsequent business logic are handled entirely by the internal mechanisms of the mini-winter framework.

### 4.2. Implement of MVC

In this section, we will refine the `DispatcherServlet` by first defining annotations such as `@Controller` and `@RestController` to mark classes and then creating annotations like `@GetMapping` to decorate methods. These methods bind to specific endpoint paths, as shown in the following example:

```java
@GetMapping("/hello/{name}")
    @ResponseBody
    String hello(@PathVariable("name") String name) {
        return "Hello, " + name;
    }
```

Consequently, the `DispatcherServlet` takes charge of scanning for all classes annotated with `@Controller` or `@RestController` while defining "dispatchers" that act as handlers for specific URLs. We also define the parameters for these methods and categorize them into four distinct types, which include `PATH_VARIABLE` for path parameters extracted from the URL, `REQUEST_PARAM` for parameters extracted from the query string or form data, `REQUEST_BODY` for data extracted from JSON payloads in POST requests, and `SERVLET_VARIABLE` for standard objects retrieved via the `HttpServletRequest` API.

We utilize reflection to inspect the methods and register these dispatcher instances, as illustrated below:

```java
PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                checkMethod(m);
                this.postDispatchers.add(new Dispatcher("POST", isRest, instance, m, post.value()));
            }
```

Subsequently, we can invoke the logic within the `doGet` method because it overrides the standard `HttpServlet` implementation. This method distinguishes between handling static resource files and processing regular service requests:

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

Finally, within `handleRestResult` and `handleMvcResult`, we process the return values from the dispatchers based on their specific types. A `void` return type indicates that the request has been handled internally, whereas a `String` might represent a view name or trigger a redirection if it starts with "redirect:". Additionally, a `String` or `byte[]` accompanied by `@ResponseBody` implies the content is written directly to the response, while a `ModelAndView` object signifies an MVC response containing both model and view data that requires rendering by the `FreeMarkerViewResolver`.

### 4.3. Create WebApp

First, create the application.yml file within the src/main/resources directory:

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

Next, you will create the core components , which include the HelloConfiguration class and related Service and Controller classes, these will be registered as Beans within the Mini Winter framework. The Service layer will be responsible for operations related to the User database. While the Web layer will house the Controller and Filter components.

Furthermore, you still need to create static files, and crucially, provide the configuration for web containers like Tomcat via the necessary web.xml file.

Finally, to deploy the application, you only need to place the generated WAR file into your Tomcat's webapps directory. Once the server is running, you can view the web application by navigating to `localhost:8080` in your browser.

### 4.4. Winter Boot

In the previous chapter, we successfully implemented a web application. However, in a traditional development and deployment scenario, the workflow involves packaging the application, copying files, and manually starting an external Tomcat server, which can be quite complex. To simplify this process, we can integrate the web application directly with the Winter framework to create the final "Winter Boot". This eliminates the need to install an external Tomcat or copy WAR files; the application can be run directly from a single JAR file.

### 4.5. Start the embedded Tomcat

When the program executes `WinterApplication.run()`, it initiates the **embedded Tomcat**.

Since we are bypassing the traditional `web.xml` configuration, we must use `addWebapp` and `WebResourceRoot` to manually **mount the file system** and map the virtual paths to the physical location of our compiled class files.

Subsequently, we register a `ServletContainerInitializer`. Tomcat triggers this initializer during its startup phase. This hook is responsible for the critical bootstrap process: creating the `AnnotationConfigApplicationContext` (initializing the IoC container) and invoking `WebUtils.registerDispatcherServlet` to register the DispatcherServlet.

### 4.6. Implementing the Boot App

In our code, we use the `jarFile` path to determine whether the application is running within an IDE or as a packaged artifact via `java -jar`. Consequently, the `webDir` (the path for static resources) and `baseDir` (the path for compiled Java files) are adjusted accordingly.

If we strictly follow the original tutorial, the code runs correctly within an IDE. However, executing the packaged artifact via `java -jar ./target/hello-boot.war` fails with a `NoClassDefFoundError`, such as:
`Exception in thread "main" java.lang.NoClassDefFoundError: com/kaiyikang/winter/boot/WinterApplication`

The error stems from the JVM startup process. After packaging the source code with `mvn clean package` and running the WAR file:

1. The JVM reads the `MANIFEST.MF` file, which contains:
   - `Main-Class: com.kaiyikang.hello.Main`
   - `Class-Path: tmp-webapp/WEB-INF/lib/...`
2. At this precise moment (startup), the `tmp-webapp` directory **does not exist yet**.
3. Consequently, the JVM invalidates these paths (removes them from its internal classpath list) and proceeds to load only `Main.class` from the WAR.
4. Although `Main.java` executes and successfully extracts the files to create the `tmp-webapp` directory, the damage is done. When the code attempts to call `WinterApplication.run`, the JVM fails because the class was not in the initial classpath lookup, resulting in a `NoClassDefFoundError`.

To resolve this, we must manually create a new ClassLoader _after_ the WAR extraction (i.e., after `tmp-webapp` is created) and use it to load `WinterApplication`.

We achieve this by instantiating a custom `appClassLoader` using `new URLClassLoader`. However, there are several critical pitfalls to address:

1. **Tomcat Configuration:** Embedded Tomcat may not automatically recognize this custom ClassLoader and might default to the system loader. Therefore, inside `WinterApplication`, we must explicitly set the parent class loader for Tomcat:
   `ctx.setParentClassLoader(Thread.currentThread().getContextClassLoader());`

2. **The "Zombie Directory" Issue:** This is the most critical problem. A "Zombie Directory" refers to a residual `tmp-webapp` folder remaining from a previous run. During startup, the JVM detects this existing folder and automatically adds it to the `AppClassLoader`'s search path. If our custom ClassLoader defaults to using `AppClassLoader` as its parent, Java's **Parent Delegation Model** dictates that the parent attempts to load the class first. The `AppClassLoader` will eagerly load the classes from the "zombie" directory (the old version), leading to version mismatches or missing dependency errors.

We resolve this by implementing ClassLoader isolation. When creating the `URLClassLoader`, we explicitly set its parent to **`PlatformClassLoader`** (which is the parent of `AppClassLoader` and is only responsible for JDK extension libraries).

This effectively cuts off the delegation to `AppClassLoader`, bypasses interference from the zombie directory, and forces our custom loader to load the freshly extracted Jar packages.

## 5. Thinking

1. Read the class or method before writing it, thinking about its functionalities and how it is written.
2. Starting with unittest makes it easier to understand.

## 6. Timeline

- 2025.09.05 ResourceResolver Done
- 2025.09.09 PropertyResolver Done
- 2025.09.17 BeanDefinition Done
- 2025.09.19 Create BeanInstance Done
- 2025.09.24 BeanPostProcessor Done
- 2025.09.24 IOC Done
- 2025.09.26 ProxyResolver Done
- 2025.10.17 Around Done
- 2025.11.03 JdbcTemplate Done
- 2025.11.04 Transactional Done
- 2025.11.23 Boot WebApp Done
- 2025.12.10 Implement of MVC Done
- 2025.12.10 Create WebApp Done
- 2025.12.10 Start the embedded Tomcat Done
- 2025.12.13 Implementing the Boot App Done
