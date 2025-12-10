# mini-winter

The simplified version of Java Spring Framework

## Source

For reference:

- [mini-spring](https://github.com/DerekYRC/mini-spring)
- [summer-framework](https://liaoxuefeng.com/books/summerframework/introduction/index.html)

## IOC - Inversion Of Control

### Load Classes and Files

We need to load all the classes first before we can go into the container.

In unit tests, compiled test classes end up under the test classpath (e.g., Maven: target/test-classes/com/kaiyikang/scan/\*), and resources are copied to the same classpath root with the same package layout (e.g., target/test-classes/com/kaiyikang/scan/sub1.txt).

### Property Resolver

To parse queries of the type `${app.title:default}`, a recursive approach can be adopted to handle highly complex situations, such as `${app.path:${app.home:${ENV_NOT_EXIST:/not-exist}}}`.

First, the core `public String getProperty(String key)` method is called. If the `key` can be continuously parsed by `PropertyExpr parsePropertyExpr(String key)` to yield a default value, it means the recursion has not ended. Once the recursion is complete, a similar logic is used to resolve the resulting value, as the value itself could also be a string like `${app.title:default}`, until a final value is reached. If the final value is null, an error is thrown; otherwise, the value is returned.

The specific design is quite ingenious. For details, please refer to `PropertyResolver.getProperty()`.

### BeanDefinition

A `BeanDefinition` is a core class designed to hold all metadata for a Bean, such as its class name, scope, and lifecycle callbacks.

Beans are primarily defined in two ways:

1. **Class-based Beans**: When a class is annotated with a stereotype like `@Component`, the class itself serves as the Bean's definition.
2. **Factory-method Beans**: In a `@Configuration` class, methods annotated with `@Bean` act as factories to create Beans.

Notably, the `beanClass` field in a `BeanDefinition` stores the Bean's **declared type** (e.g., an interface), not its actual runtime type (e.g., the implementation class). This declared type is crucial for dependency injection and type lookups, while the actual type can only be determined via `instance.getClass()` after creation.

During the development of the Bean loading and initialization logic, a strong emphasis was placed on robustness. The pre-condition validation was enhanced with a **fail-fast** strategy to catch configuration errors early, preventing hard-to-diagnose `NullPointerExceptions` at runtime.

The purpose of the `AnnotationConfigApplicationContext` is to scan for and collect all classes with valid annotations, create corresponding `BeanDefinition`s, and organize them into an internal registry (a Map) indexed by bean name. It then uses this registry to locate and serve Bean instances upon request.

### BeanInstance

**Strong dependencies**, those supplied through constructor or factory method injection, cannot have circular references. The container must throw an error if such a dependency loop is found. In contrast, **weak dependencies** allow for circular references by separating instantiation from property injection.

This leads to a two-phase bean creation process:

1. **Instantiation:** The bean instance is created by invoking its constructor, which resolves all strong dependencies.
2. **Population:** The instance is then populated with weak dependencies through setter and field injection.

Regarding API naming conventions: `get` methods are expected to always return an object (or throw an exception), whereas `find` methods may return `null` if the object is not found. See `getBean` and `findBean` for a practical example.

For an implementation detail on weak dependencies, consider the `tryInjectProperties` method. It takes the target `bean` instance and an `acc` object (representing the field or method to be injected). The process involves making the member accessible via `setAccessible(true)`, resolving the dependency bean that needs to be injected, and finally, using reflection to set the field's value or invoke the setter method with the resolved dependency.

### BeanPostProcessor

The current process is as follows:

1. Create the bean definition.
2. Create an instance based on the definition.
3. Inject other instances into this instance.
4. The complete bean is ready.

A new requirement has emerged: if we need to replace a bean or add new logic to an already instantiated bean, we must use a `BeanPostProcessor`. This happens around steps 2 and 3.

After step 2, a `BeanProxy` can be created and returned to the factory, replacing the original bean. For step 3, there are two important points to note:

1. For example, if a `Controller` needs a bean to be injected, the `BeanProxy` should be injected, not the original bean.
2. If this bean itself needs other dependencies, should they be injected into the original bean or the `BeanProxy`? The answer is the **original bean**, because the original bean is the one that actually performs the operations.

In summary:

- To any bean that depends on me, expose the proxy object.
- For any dependency I have, inject it into the original object.

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

### Finish IOC

Finish interface.

## AOP - Aspect-Oriented Programming

**Aspect-Oriented Programming (AOP)** adds extra, common functionality to already assembled Bean objects. This functionality is characterized by not being part of the core business logic, yet it cuts across methods in different business areas. AOP adds a proxy layer to a Bean. When a bean's method is called, the proxy's method is invoked first, which then lets the bean handle the request, and finally, some finishing work can be added.

There are three ways to implement this:

- **Compile-time**: The "add-on" code is directly woven into the `.class` file during compilation. It offers the best performance but is the most complex, requiring a special compiler.
- **Load-time**: When a `.class` file is being loaded into the JVM's memory, it is intercepted, and the "add-on" logic is injected. This is flexible but requires an understanding of the JVM's class-loading mechanism.
- **Run-time**: The "add-on" code is a regular Java class, which is dynamically generated as a proxy object for the Bean. This is the most common and simplest approach.

mini-winter only supports the annotation-based approach, where a Bean clearly knows what "add-on" it has. It supports proxying all types of classes and uses Byte Buddy as its implementation mechanism.

### ProxyResolver

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

### Around

The `@Polite` annotation is applied to methods to indicate that they require special processing. For classes that need AOP applied, use `@Around("aroundInvocationHandler")`, where the value specifies which handler the framework should use to process the aspect.

The `AroundInvocationHandler` is a regular Bean marked with `@Component`. To wire the handler into the application, use `@Configuration` and `@ComponentScan`, and annotate the creation of the `AroundProxyBeanPostProcessor` object with `@Bean` to let the container manage it. Subsequently, `AroundProxyBeanPostProcessor` scans Bean classes to check if they contain the `@Around` annotation. If present, it creates and returns a proxy for the original Bean; otherwise, it returns the Bean unmodified.

Additionally, to implement **before** or **after** patterns, the adapter pattern can be used by providing `BeforeInvocationHandlerAdapter` and `AfterInvocationHandlerAdapter`.

Finally, to implement AOP using custom annotations (e.g., `@Transactional`), you can use the generic base class `AnnotationProxyBeanPostProcessor<A extends Annotation>`. Simply create a class that extends `AnnotationProxyBeanPostProcessor<Transactional>` to achieve this.

## JDBC - Java DataBase Connectivity & Transactions

A **transaction** is a fundamental concept in database operations that guarantees **ACID** properties:

- **Atomicity**: All operations within the transaction either succeed entirely, or they all fail and are rolled back. It's an "all-or-nothing" principle.
- **Consistency**: The database remains in a consistent state after the transaction is completed. It transitions from one valid state to another.
- **Isolation**: Concurrent transactions do not affect each other, and the intermediate state of one transaction is not visible to others.
- **Durability**: Once a transaction is committed, its changes to the database are permanent and will survive system failures.

This chapter will cover the implementation of **declarative transactions**, notably through the `@Transactional` annotation. When a method marked with this annotation is executed, the underlying framework automatically manages the transaction lifecycle (begin, commit, rollback) to enforce these ACID properties.

### JdbcTemplate

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

### The `@Transactional` Annotation

The `@Transactional` annotation, by leveraging a transaction lifecycle, effectively ensures the **atomicity** of operations.

The `DataSourceTransactionManager` class, when invoked via an AOP mechanism (as orchestrated by your custom `AnnotationProxyBeanPostProcessor<Transactional>`), manages transactions by utilizing a `ThreadLocal`.

This `ThreadLocal` is used to bind the current transaction to the executing thread.

- **If a transaction is already bound to the current thread** (i.e., the `ThreadLocal` contains an active transaction), the `DataSourceTransactionManager` will typically **join this existing transaction** for subsequent method invocations within the same thread.
- **Otherwise**, if no transaction is currently bound, it will **initiate a new transaction**.

This scenario of joining an existing transaction is particularly relevant in **nested method calls** where an outer method has already started a transaction.

Consequently, utility methods like `TransactionalUtils.getCurrentConnection()` can retrieve the database connection that is currently bound to the active transaction on the current thread.

## Implementation of MVC

### Boot WebApp

In this section, our goal is to create a WebApp using the current mini-winter framework and package it as a WAR (Web Application Archive) file. When the Tomcat server starts, it will scan for and load this WAR file, ensuring that when the corresponding URL is accessed in a browser, the WebApp's response is correctly displayed.

A typical Java web application adheres to the Servlet Specification. The Servlet Specification defines not only the interfaces that a WebApp must implement but also how a web server (like Tomcat) should load the WebApp, the order in which requests are processed, and how various components are invoked. This establishes a clear decoupling model. The Servlet Specification defines three core component types:

1.  **Listener**: Listens for lifecycle events of the WebApp, such as application startup and shutdown, as well as Session creation and attribute changes.
2.  **Filter**: Executes before an HTTP request reaches the final Servlet. It is used for tasks like authorization, rate limiting, logging, and cache validation.
3.  **Servlet**: Ultimately handles the HTTP request, determining the business logic to execute for GET, POST, or other methods, and generating the response.

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

1.  **Create the `ApplicationContext`**: This is the IoC (Inversion of Control) container for mini-winter, responsible for component scanning, bean instantiation, and dependency management.
2.  **Register the `DispatcherServlet` and map it to the root path `/`**: During its initialization, the `DispatcherServlet` obtains a reference to the `ApplicationContext` (created in the first step), which allows it to access all scanned controllers and service components.

Thus, once the WebApp has started successfully, the `DispatcherServlet` acts as the "Front Controller" for the entire application. When any user sends an HTTP request to this WebApp, Tomcat's URL matching rules will delegate the request to the `DispatcherServlet` for processing. Since it is mapped to `/`, virtually all paths (with a few exceptions) will be routed to it.

The `DispatcherServlet` already holds a complete reference to the `ApplicationContext`. Therefore, when processing a request, it can directly access components within the mini-winter framework, such as Controllers and Services, without relying on the traditional dispatching mechanisms of the Servlet API. The request routing and subsequent business logic are handled entirely by the internal mechanisms of the mini-winter framework.

### Implement of MVC

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

## Thinking

1. Read the class or method before writing it, thinking about its functionalities and how it is written.
2. Starting with unittest makes it easier to understand.

## Timeline

2025.09.05 ResourceResolver Done
2025.09.09 PropertyResolver Done
2025.09.17 BeanDefinition Done
2025.09.19 Create BeanInstance Done
2025.09.24 BeanPostProcessor Done
2025.09.24 IOC Done
2025.09.26 ProxyResolver Done
2025.10.17 Around Done
2025.11.03 JdbcTemplate Done
2025.11.04 Transactional Done
2025.11.23 Boot WebApp Done
2025.12.10 Implement of MVC Done
