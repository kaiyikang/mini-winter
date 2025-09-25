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

Aspect Oriented Programming, 为组装好的 Bean 对象增加额外通用的功能。这些功能的特点是，不属于核心业务，但又横跨了不同业务的方法。AOP 会为 Bean 加一层代理，当调用 bean 方法时，先调用代理方法，然后再让 bean 处理请求，最后还可以加入些收尾工作。

有三种方法实现：

- 编译期：将外挂代码直接在编译成 class 文件时缝入。性能好，但需要特殊编译器，最复杂。
- 类加载期：当 class 被加载到 JVM 内存之前，进行拦截，加入外挂逻辑。灵活，但需要理解 JVM 加载机制。
- 运行期：外挂代码是普通 java 类，被动态生成为 Bean 的代理对象。最常用和简单。

mini-winter 只支持注解方式，Bean 会明确知道外挂的样子，支持处理所有的类，实现的机制选择 Byte Buddy。

## ProxyResolver

两个东西必不可少：

1. 原始的 Bean
2. 拦截器 Interceptor：拦截目标 Bean 方法，自动调用拦截器实现代理功能。

执行的流程是：

1. 调用代理的方法
2. 代理将调用转发至拦截器
3. 拦截器执行额外逻辑，并决定合适调用原始的 Bean 方法
4. 执行原始 Bean 方法
5. 拦截器处理结果
6. 代理返回结果

AOP 弱化了具体的方法，字段等概念，而强化了动态的概念，例如「方法调用」。如果按照 AOP 的术语描述，目标对象的方法调用，字段访问等类似的事件点，都被定义成 Join Point。我们感兴趣的那些 Join Point，被额外筛选出来的，都被称为 Pointcut。

对 Pointcut 具体做什么，则叫做 Advice。

Joint Point 和 Advice 统一起来，被称为 Aspect。

## Thinking

1. Read the class or method before writing it, thinking about its functionalities and how it is written.

## Timeline

2025.09.05 ResourceResolver Done
2025.09.09 PropertyResolver Done
2025.09.17 BeanDefinition Done
2025.09.19 Create BeanInstance Done
2025.09.24 BeanPostProcessor Done
2025.09.24 IOC Done
