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

BeanDefinition 是一个专门的类，用来存放一个 Bean 的所有元数据（描述信息）。

带 @Component 注解的类，这种 Bean 最直接，类本身就是定义。对于@Configuration 类中带 @Bean 注解的方法本身负责创建 Bean。

注意 beanClass 字段存放的是 Bean 的声明类型，而不是实际类型。记录声明类型就够了，因为这对依赖注入和类型查找至关重要。至于实际类型，等创建了实例 instance 之后，可通过 instance.getClass() 获得。

尤其是在 bean 加载和初始化的部分，源码这里的 fail-fast 和 pre-condition validation 做的不够好，这里进行了改进和微小的重构。

## Thinking

1. Read the class or method before writing it, thinking about its functionalities and how it is written.

## Timeline

2025.09.05 ResourceResolver Done
2025.09.09 PropertyResolver Done
