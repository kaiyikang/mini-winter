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

## Thinking

1. Read the class or method before writing it, thinking about its functionalities and how it is written.

## Timeline

2025.09.05 ResourceResolver Done
2025.09.09 PropertyResolver Done
2025.09.17 BeanDefinition Done
