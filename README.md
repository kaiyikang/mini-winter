# mini-winter

The simplified version of Java Spring Framework

## Source

For reference:

- [mini-spring](https://github.com/DerekYRC/mini-spring)
- [summer-framework](https://liaoxuefeng.com/books/summerframework/introduction/index.html)

## IOC

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

1.  **Class-based Beans**: When a class is annotated with a stereotype like `@Component`, the class itself serves as the Bean's definition.
2.  **Factory-method Beans**: In a `@Configuration` class, methods annotated with `@Bean` act as factories to create Beans.

Notably, the `beanClass` field in a `BeanDefinition` stores the Bean's **declared type** (e.g., an interface), not its actual runtime type (e.g., the implementation class). This declared type is crucial for dependency injection and type lookups, while the actual type can only be determined via `instance.getClass()` after creation.

During the development of the Bean loading and initialization logic, a strong emphasis was placed on robustness. The pre-condition validation was enhanced with a **fail-fast** strategy to catch configuration errors early, preventing hard-to-diagnose `NullPointerExceptions` at runtime.

The purpose of the `AnnotationConfigApplicationContext` is to scan for and collect all classes with valid annotations, create corresponding `BeanDefinition`s, and organize them into an internal registry (a Map) indexed by bean name. It then uses this registry to locate and serve Bean instances upon request.

### BeanInstance

**Strong dependencies**, those supplied through constructor or factory method injection, cannot have circular references. The container must throw an error if such a dependency loop is found. In contrast, **weak dependencies** allow for circular references by separating instantiation from property injection.

This leads to a two-phase bean creation process:

1.  **Instantiation:** The bean instance is created by invoking its constructor, which resolves all strong dependencies.
2.  **Population:** The instance is then populated with weak dependencies through setter and field injection.

Regarding API naming conventions: `get` methods are expected to always return an object (or throw an exception), whereas `find` methods may return `null` if the object is not found. See `getBean` and `findBean` for a practical example.

For an implementation detail on weak dependencies, consider the `tryInjectProperties` method. It takes the target `bean` instance and an `acc` object (representing the field or method to be injected). The process involves making the member accessible via `setAccessible(true)`, resolving the dependency bean that needs to be injected, and finally, using reflection to set the field's value or invoke the setter method with the resolved dependency.

### BeanPostProcessor

The current process is as follows:

1.  Create the bean definition.
2.  Create an instance based on the definition.
3.  Inject other instances into this instance.
4.  The complete bean is ready.

A new requirement has emerged: if we need to replace a bean or add new logic to an already instantiated bean, we must use a `BeanPostProcessor`. This happens around steps 2 and 3.

After step 2, a `BeanProxy` can be created and returned to the factory, replacing the original bean. For step 3, there are two important points to note:

1.  For example, if a `Controller` needs a bean to be injected, the `BeanProxy` should be injected, not the original bean.
2.  If this bean itself needs other dependencies, should they be injected into the original bean or the `BeanProxy`? The answer is the **original bean**, because the original bean is the one that actually performs the operations.

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

## AOP

**Aspect-Oriented Programming (AOP)** adds extra, common functionality to already assembled Bean objects. This functionality is characterized by not being part of the core business logic, yet it cuts across methods in different business areas. AOP adds a proxy layer to a Bean. When a bean's method is called, the proxy's method is invoked first, which then lets the bean handle the request, and finally, some finishing work can be added.

There are three ways to implement this:

- **Compile-time**: The "add-on" code is directly woven into the `.class` file during compilation. It offers the best performance but is the most complex, requiring a special compiler.
- **Load-time**: When a `.class` file is being loaded into the JVM's memory, it is intercepted, and the "add-on" logic is injected. This is flexible but requires an understanding of the JVM's class-loading mechanism.
- **Run-time**: The "add-on" code is a regular Java class, which is dynamically generated as a proxy object for the Bean. This is the most common and simplest approach.

mini-winter only supports the annotation-based approach, where a Bean clearly knows what "add-on" it has. It supports proxying all types of classes and uses Byte Buddy as its implementation mechanism.

### ProxyResolver

Two things are essential:

1.  The original Bean.
2.  The Interceptor: It intercepts the target Bean's method calls and automatically invokes the interceptor to implement the proxy functionality.

The execution flow is as follows:

1.  The proxy's method is called.
2.  The proxy forwards the call to the interceptor.
3.  The interceptor executes its extra logic and decides when to call the original Bean's method.
4.  The original Bean's method is executed.
5.  The interceptor processes the result.
6.  The proxy returns the final result.

AOP de-emphasizes concrete concepts like methods and fields, instead emphasizing dynamic concepts like "method invocation." In AOP terminology, event points like method calls or field access on a target object are defined as **Join Points**. The specific Join Points that we are interested in, which are filtered out, are defined by a **Pointcut**.

What to do at a Pointcut is called an **Advice**.

An **Aspect** is the combination of a **Pointcut** and an **Advice**. It modularizes a concern that cuts across multiple types and objects by defining "where" (the Pointcut) and "what" (the Advice) to do.

### Around

@Polite 针对方法，表示需要被特别处理。针对需要应用 AOP 的类，使用 @Around("aroundInvocationHandler")，其中的值，告诉框架，使用该 handler 处理 Aspect。

AroundInvocationHandler 是一个普通的 Bean，被标记了 @Component。为了将 handler 装配进应用，使用 @Configuration, @ComponentScan 并使用 @Bean 标记创建 AroundProxyBeanPostProcessor 对象，交给 spring 管理。随后，AroundProxyBeanPostProcessor 会扫描 Bean 类是否包含@Around，如果包含，创建返回 originBean 的代理，否则不做处理，直接返回。

另外，为了实现 before，或 after 模式，可以使用 adapter 模式，提供额外的 BeforeInvocationHandlerAdapter 以及 AfterInvocationHandlerAdapter 即可。

最后，如果想要使用自定义的注解来实现 AOP，比如标注@Transactional，则可以使用通用基类 AnnotationProxyBeanPostProcessor<A extends Annotation>，最写一个类继承 AnnotationProxyBeanPostProcessor<Transactional>，就可以实现。

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
