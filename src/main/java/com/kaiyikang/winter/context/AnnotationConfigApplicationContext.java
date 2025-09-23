package com.kaiyikang.winter.context;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Import;
import com.kaiyikang.winter.annotation.Order;
import com.kaiyikang.winter.annotation.Primary;
import com.kaiyikang.winter.annotation.Value;
import com.kaiyikang.winter.exception.BeanCreationException;
import com.kaiyikang.winter.exception.BeanDefinitionException;
import com.kaiyikang.winter.exception.BeanNotOfRequiredTypeException;
import com.kaiyikang.winter.exception.NoSuchBeanDefinitionException;
import com.kaiyikang.winter.exception.NoUniqueBeanDefinitionException;
import com.kaiyikang.winter.exception.UnsatisfiedDependencyException;
import com.kaiyikang.winter.io.PropertyResolver;
import com.kaiyikang.winter.io.ResourceResolver;
import com.kaiyikang.winter.utils.ClassUtils;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class AnnotationConfigApplicationContext {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final PropertyResolver propertyResolver;
    protected final Map<String, BeanDefinition> beans;

    private List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();
    private Set<String> creatingBeanNames;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;

        // Scan to obtain the Class type of all Beans
        final Set<String> beanClassNames = scanForClassNames(configClass);

        // Create Map for name:beanDef
        this.beans = createBeanDefinitions(beanClassNames);

        // Create a set to detect the circular dependency
        this.creatingBeanNames = new HashSet<>();

        // Instance Beans with @Configuration (should be first due to factory)
        this.beans.values().stream()
                .filter(this::isConfigurationDefinition).sorted().map(def -> {
                    createBeanAsEarlySingleton(def);
                    return def.getName();
                }).toList();

        // Create BeanPostProcessor Bean
        List<BeanPostProcessor> processors = this.beans.values().stream()
                .filter(this::isBeanPostProcessorDefinition)
                .sorted()
                .map(def -> {
                    return (BeanPostProcessor) createBeanAsEarlySingleton(def);
                }).toList();
        this.beanPostProcessors.addAll(processors);

        // Instance other normal beans
        createNormalBeans();

        // Inject dependency by field and setter
        this.beans.values().forEach(def -> {
            injectBean(def);
        });

        // Call init method
        this.beans.values().forEach(def -> {
            initBean(def);
        });

        if (logger.isDebugEnabled()) {
            this.beans.values().stream().sorted().forEach(def -> {
                logger.debug("bean initialized: {}", def);
            });
        }

        // Clean up
        this.creatingBeanNames = null;
    }

    /// Bean Definition
    // -----------------

    /**
     * Collect a collection of class names (Strings) for all candidate Beans that
     * need to be managed by the IoC container.
     * 
     */
    protected Set<String> scanForClassNames(Class<?> configClass) {

        // Get the scanned scope, like: [com.example.service] or default [com.example]
        // Beans are saved in this scope.
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        final String[] scanPackages = scan == null || scan.value().length == 0
                ? new String[] { configClass.getPackage().getName() }
                : scan.value();
        logger.atInfo().log("component scan in packages: {}", Arrays.toString(scanPackages));

        // Do the scan
        Set<String> classNameSet = new HashSet<>();
        for (String pkg : scanPackages) {
            // scan
            logger.atDebug().log("scan package: {}", pkg);
            var resolver = new ResourceResolver(pkg);
            List<String> classList = resolver.scan(res -> {
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            // log
            if (logger.isDebugEnabled()) {
                classList.forEach((className) -> {
                    logger.debug("class found by component scan: {}", className);
                });
            }
            classNameSet.addAll(classList);
        }

        // Import specific class, like: @Import(Xyz.class):
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig == null) {
            return classNameSet;
        }

        for (Class<?> importConfigClass : importConfig.value()) {
            String importClassName = importConfigClass.getName();
            if (classNameSet.contains(importClassName)) {
                logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
            } else {
                logger.debug("class found by import: {}", importClassName);
                classNameSet.add(importClassName);
            }
        }

        return classNameSet;
    }

    /**
     * Create BeanDefinition based on the scanned ClassName
     * 
     */
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        Map<String, BeanDefinition> definitions = new HashMap<>();

        for (final String className : classNameSet) {
            // Load class
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new BeanCreationException(e);
            }

            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
                continue;
            }

            // Verify the @Component Annotation
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if (component == null) {
                continue;
            }

            logger.atDebug().log("found component: {}", clazz.getName());
            int modifier = clazz.getModifiers();
            if (Modifier.isAbstract(modifier)) {
                throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be abstract.");
            }
            if (Modifier.isPrivate(modifier)) {
                throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be private.");
            }

            String beanName = ClassUtils.getBeanName(clazz);
            var definition = new BeanDefinition(beanName, clazz, getSuitableConstructor(clazz), getOrder(clazz),
                    clazz.isAnnotationPresent(Primary.class), null, null,
                    ClassUtils.findAnnotationMethod(clazz, PostConstruct.class),
                    ClassUtils.findAnnotationMethod(clazz, PreDestroy.class));

            // Add bean from class
            addBeanDefinitions(definitions, definition);
            logger.atDebug().log("define bean: {}", definition);

            Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
            if (configuration != null) {
                if (BeanPostProcessor.class.isAssignableFrom(clazz)) {
                    throw new BeanDefinitionException(
                            "@Configuration class '" + clazz.getName() + "' cannot be BeanPostProcessor.");
                }
                scanFactoryMethods(beanName, clazz, definitions);
            }
        }

        return definitions;
    }

    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        // Find public constructor
        Constructor<?>[] publicConstructors = clazz.getConstructors();
        if (publicConstructors.length == 1) {
            return publicConstructors[0];
        }

        if (publicConstructors.length > 1) {
            throw new BeanDefinitionException(
                    "More than one public constructor found in class " + clazz.getName() + ".");
        }

        // Find all declared constructor due to publicConstructors.length == 9
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
        if (declaredConstructors.length == 1) {
            return declaredConstructors[0];
        }

        if (declaredConstructors.length == 0) {
            throw new BeanDefinitionException("No constructor found in class " + clazz.getName() + ".");
        } else {
            throw new BeanDefinitionException(
                    "No public constructor found, and multiple non-public constructors found in class "
                            + clazz.getName() + ".");
        }

    }

    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();

    }

    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    /**
     * Scan factory method that annotated with @Bean
     * 
     * <code>
     * &#64;Configuration
     * public class Hello {
     * 
     * @Bean
     * ZoneId createZone() {
     * return ZoneId.of("Z");
     * }
     * }
     * </code>
     */
    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> definitions) {
        for (Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean == null) {
                continue;
            }
            // Verify modifier of the method
            int mod = method.getModifiers();
            if (Modifier.isAbstract(mod)) {
                throw new BeanDefinitionException(
                        "@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
            }
            if (Modifier.isFinal(mod)) {
                throw new BeanDefinitionException(
                        "@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
            }
            if (Modifier.isPrivate(mod)) {
                throw new BeanDefinitionException(
                        "@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
            }

            Class<?> beanClass = method.getReturnType();
            if (beanClass.isPrimitive()) {
                throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName()
                        + " must not return primitive type.");
            }
            if (beanClass == void.class || beanClass == Void.class) {
                throw new BeanDefinitionException(
                        "@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
            }

            var definition = new BeanDefinition(
                    ClassUtils.getBeanName(method),
                    beanClass,
                    factoryBeanName,
                    method,
                    getOrder(method),
                    method.isAnnotationPresent(Primary.class),
                    bean.initMethod().isEmpty() ? null : bean.initMethod(),
                    bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                    null, null);

            // Add bean from method initialization
            addBeanDefinitions(definitions, definition);
            logger.atDebug().log("define bean: {}", definition);
        }
    }

    boolean isConfigurationDefinition(BeanDefinition def) {
        return ClassUtils.findAnnotation(def.getBeanClass(), Configuration.class) != null;
    }

    /**
     * Find Bean by name, return null if not exist
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * Find Bean by name and type
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format(
                    "Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                    name, def.getBeanClass().getName()));
        }
        return def;
    }

    /*
     * Find beans by type
     */
    public List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream().filter(def -> type.isAssignableFrom(def.getBeanClass()))
                .sorted().toList();
    }

    /**
     * Find bean by type
     */
    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if (defs.isEmpty()) {
            return null;
        }
        if (defs.size() == 1) {
            return defs.get(0);
        }

        List<BeanDefinition> primaryDefs = defs.stream().filter(def -> def.isPrimary()).toList();
        if (primaryDefs.size() == 1) {
            return primaryDefs.get(0);
        }
        if (primaryDefs.isEmpty()) {
            throw new NoUniqueBeanDefinitionException(
                    String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        } else {
            throw new NoUniqueBeanDefinitionException(String
                    .format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }

    // Bean Initialization
    // -------------------

    /**
     * Create an early singleton instance of a Bean.
     * "Early singleton" means this phase handles only strong dependencies
     * (injecting parameters into constructors and factory methods),
     * while ignoring weak dependencies (injecting @Autowired fields and setters).
     * This method is recursive; when encountering an uncreated dependency, it first
     * creates that dependency.
     */
    private Object createBeanAsEarlySingleton(BeanDefinition def) {
        logger.atDebug().log("Try create bean '{}' as early singleton: {}", def.getName(),
                def.getBeanClass().getName());

        // --- 1. Detect Circular Dependency ---
        if (!this.creatingBeanNames.add(def.getName())) {
            throw new UnsatisfiedDependencyException(
                    String.format("Circular dependency detected when create bean '%s'", def.getName()));
        }

        // -- 2. Handle factory or constructor --
        Executable createFn = null;
        if (def.getFactoryMethod() == null) {
            createFn = def.getConstructor(); // By Constructor
        } else {
            createFn = def.getFactoryMethod(); // By Factory
        }

        // -- 3. Prepare Parameters --
        final Parameter[] parameters = createFn.getParameters(); // all params
        final Annotation[][] parametersAnnos = createFn.getParameterAnnotations(); // annotation for each param
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            final Parameter param = parameters[i];
            final Annotation[] paramAnnos = parametersAnnos[i];

            final Value value = ClassUtils.getAnnotation(paramAnnos, Value.class);
            final Autowired autowired = ClassUtils.getAnnotation(paramAnnos, Autowired.class);

            // Verify Configuration
            final boolean isConfiguration = isConfigurationDefinition(def);
            if (isConfiguration && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s.",
                                def.getName(), def.getBeanClass().getName()));
            }
            final boolean isBeanPostProcessor = isBeanPostProcessorDefinition(def);
            if (isBeanPostProcessor && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify @Autowired when create BeanPostProcessor '%s': %s.",
                                def.getName(), def.getBeanClass().getName()));
            }
            if (value != null && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify both @Autowired and @Value when create bean '%s': %s.",
                                def.getName(), def.getBeanClass().getName()));
            }
            if (value == null && autowired == null) {
                throw new BeanCreationException(
                        String.format("Must specify @Autowired or @Value when create bean '%s': %s.", def.getName(),
                                def.getBeanClass().getName()));
            }

            // Analysis the parameters
            final Class<?> type = param.getType();
            if (value != null) {
                // @Value
                args[i] = this.propertyResolver.getRequiredProperty(value.value(), type);
            } else {
                // @Autowired, need to inject a new bean
                String name = autowired.name();
                boolean required = autowired.value();

                BeanDefinition dependsOnDef = name.isEmpty() ? findBeanDefinition(type)
                        : findBeanDefinition(name, type);

                // Dependency is required but cannot find it in defs
                if (required && dependsOnDef == null) {
                    throw new BeanCreationException(String.format(
                            "Missing autowired bean with type '%s' when create bean '%s': %s.", type.getName(),
                            def.getName(), def.getBeanClass().getName()));
                }

                if (dependsOnDef == null) {
                    // Dependency is not required, but cannot find, just inject null
                    args[i] = null;
                    continue;
                }

                // Find the dependency
                Object autowiredBeanInstance = dependsOnDef.getInstance();
                if (autowiredBeanInstance == null && !isConfiguration && !isBeanPostProcessor) {
                    autowiredBeanInstance = createBeanAsEarlySingleton(dependsOnDef);
                }
                args[i] = autowiredBeanInstance;
            }
        }

        // -- 4. Create Bean Instance --
        Object instance = null;
        if (def.getFactoryName() == null) {
            // Constrictor
            try {
                instance = def.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(),
                        def.getBeanClass().getName()), e);
            }
        } else {
            // Factory - to get factory bean instance @Configuration first
            Object configInstance = getBean(def.getFactoryName());
            try {
                // invoke the method by reflection
                instance = def.getFactoryMethod().invoke(configInstance, args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(),
                        def.getBeanClass().getName()), e);
            }
        }

        // --- 5. Save ---
        def.setInstance(instance);

        // --- 6. BeanPostProcessor handle bean ---
        for (BeanPostProcessor processor : beanPostProcessors) {
            Object processed = processor.postProcessBeforeInitialization(def.getInstance(), def.getName());
            if (processed == null) {
                throw new BeanCreationException(String.format(
                        "PostBeanProcessor returns null when process bean '%s' by %s", def.getName(), processor));
            }
            // Update the ref of the bean if the origin bean is replaced
            if (def.getInstance() != processed) {
                logger.atDebug().log("Bean '{}' was replaced by post processor {}.", def.getName(),
                        processor.getClass().getName());
                def.setInstance(processed);
            }
        }

        return def.getInstance();
    }

    void createNormalBeans() {
        List<BeanDefinition> defs = this.beans.values().stream().filter(def -> def.getInstance() == null).sorted()
                .toList();

        defs.forEach(def -> {
            if (def.getInstance() == null) {
                createBeanAsEarlySingleton(def);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition def = this.beans.get(name);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'.", name));
        }
        return (T) def.getRequiredInstance();
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type '%s'.", requiredType));
        }
        return (T) def.getRequiredInstance();
    }

    public <T> T getBean(String name, Class<T> requiredType) {
        T t = findBean(name, requiredType);
        if (t == null) {
            throw new NoSuchBeanDefinitionException(
                    String.format("No bean defined with name '%s' and type '%s'.", name, requiredType));
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(def -> (T) def.getRequiredInstance()).toList();
    }

    public boolean containsBean(String name) {
        return this.beans.containsKey(name);
    }

    // Similar to getBean(s) but return null if it doesn't exist.
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(String name, Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(name, requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> List<T> findBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(def -> (T) def.getRequiredInstance())
                .toList();
    }

    // Bean Injection
    // --------------

    /**
     * Inject Bean without init calling
     */
    void injectBean(BeanDefinition def) {

        final Object originBeanInstance = getOriginInstance(def);
        try {
            injectProperties(def, def.getBeanClass(), originBeanInstance);
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }

    void initBean(BeanDefinition def) {
        final Object beanInstance = getOriginInstance(def);

        // call init method
        callMethod(beanInstance, def.getInitMethod(), def.getInitMethodName());

        beanPostProcessors.forEach(beanPostProcessor -> {
            Object processedInstance = beanPostProcessor.postProcessAfterInitialization(def.getInstance(),
                    def.getName());
            if (processedInstance != def.getInstance()) {
                logger.atDebug().log("BeanPostProcessor {} return different bean from {} to {}.",
                        beanPostProcessor.getClass().getSimpleName(),
                        def.getInstance().getClass().getName(), processedInstance.getClass().getName());
                def.setInstance(processedInstance);
            }
        });
    }

    private void injectProperties(BeanDefinition def, Class<?> clazz, Object bean) throws ReflectiveOperationException {
        for (Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, clazz, bean, f);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, clazz, bean, m);
        }

        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            injectProperties(def, superClazz, bean);
        }
    }

    /**
     * Inject properties
     */
    void tryInjectPropertiesOrg(BeanDefinition def, Class<?> clazz, Object bean,
            AccessibleObject acc)
            throws ReflectiveOperationException {

        Value value = acc.getAnnotation(Value.class);
        Autowired autowired = acc.getAnnotation(Autowired.class);

        if (value == null && autowired == null) {
            return;
        }

        Field field = null;
        Method method = null;

        if (acc instanceof Field f) {
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        }

        if (acc instanceof Method m) {
            checkFieldOrMethod(m);
            if (m.getParameters().length != 1) {
                throw new BeanDefinitionException(
                        String.format("Cannot inject a non-setter method %s for bean '%s': %s",
                                m.getName(),
                                def.getName(), def.getBeanClass().getName()));
            }
            m.setAccessible(true);
            method = m;
        }

        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];

        // Validate value and autowired
        if (value != null && autowired != null) {
            throw new BeanCreationException(
                    String.format("Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s': %s",
                            clazz.getSimpleName(), accessibleName, def.getName(),
                            def.getBeanClass().getName()));
        }

        // Inject value
        if (value != null) {
            Object propValue = this.propertyResolver.getRequiredProperty(value.value(), accessibleType);
            if (field != null) {
                logger.atDebug().log("Field injection: {}.{} = {}",
                        def.getBeanClass().getName(), accessibleName,
                        propValue);
                field.set(bean, propValue);
            }
            if (method != null) {
                // 这部分我不知道发生了什么，意思是我对method调用了bean？
                logger.atDebug().log("Method injection: {}.{} ({})",
                        def.getBeanClass().getName(), accessibleName,
                        propValue);
                method.invoke(bean, propValue);
            }
        }
        // Q: 似乎代码都是用field和method是否为null区分的，这是否能够做一些优化？

        // Inject autowired
        if (autowired != null) {
            String name = autowired.name();
            boolean required = autowired.value();
            // Q: 这里的depends是说类似于
            // @Autowired
            // void hello(Dependency depend);之类的样子么？
            Object depends = name.isEmpty() ? findBean(accessibleType)
                    : findBean(name,
                            accessibleType);
            if (required && depends == null) {
                throw new UnsatisfiedDependencyException(String.format(
                        "Dependency bean not found when inject %s.%s for bean '%s': %s",
                        clazz.getSimpleName(),
                        accessibleName, def.getName(), def.getBeanClass().getName()));
            }
            if (depends != null) {
                if (field != null) {
                    logger.atDebug().log("Field injection: {}.{} = {}",
                            def.getBeanClass().getName(), accessibleName,
                            depends);
                    field.set(bean, depends);
                }
                if (method != null) {
                    logger.atDebug().log("Method injection: {}.{} ({})",
                            def.getBeanClass().getName(), accessibleName,
                            depends);
                    method.invoke(bean, depends);
                }
            }
        }
    }

    /**
     * Inject the bean
     * 
     * @param def   deprecated
     * @param clazz for logging
     * @param bean  need the dependencies
     * @param acc   is the dependency
     * @throws ReflectiveOperationException
     */
    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean,
            AccessibleObject acc)
            throws ReflectiveOperationException {

        // --- 1. Prepare ---
        Value value = acc.getAnnotation(Value.class);
        Autowired autowired = acc.getAnnotation(Autowired.class);
        if (value == null && autowired == null) {
            return;
        }

        // Validate: @Value and @Autowired
        if (value != null && autowired != null) {
            throw new BeanCreationException(String.format("Cannot specify both @Autowired and @Value on %s", acc));
        }

        Field field = null;
        Method method = null;
        Class<?> accessibleType; // Field type or method parameter type

        // --- 2. Detect and validate
        if (acc instanceof Field f) {
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
            accessibleType = f.getType();
        } else if (acc instanceof Method m) {
            checkFieldOrMethod(m); // 假设这个方法检查 private, static 等修饰符
            if (m.getParameterCount() != 1) {
                throw new BeanDefinitionException("Cannot inject a non-setter method with " + m.getParameterCount()
                        + " parameters: " + m.getName());
            }
            m.setAccessible(true);
            method = m;
            accessibleType = m.getParameterTypes()[0];
        } else {
            return;
        }

        // 3. --- get value to inject ---
        Object valueToInject;
        if (value != null) {
            // Handle @Value
            valueToInject = this.propertyResolver.getRequiredProperty(value.value(), accessibleType);
        } else {
            // Handle @Autowired
            String name = autowired.name();
            boolean required = autowired.value();
            // find beanInstance
            valueToInject = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);

            if (required && valueToInject == null) {
                throw new UnsatisfiedDependencyException(String.format("Dependency bean of type '%s' not found for %s",
                        accessibleType.getSimpleName(), acc));
            }
        }

        // 4. --- Execute the injection
        if (valueToInject != null) {
            if (field != null) {
                logger.atDebug().log("Field injection: {}.{} = {}", clazz.getSimpleName(), field.getName(),
                        valueToInject);
                field.set(bean, valueToInject);
            } else {
                logger.atDebug().log("Method injection: {}.{} ({})", clazz.getSimpleName(), method.getName(),
                        valueToInject);
                method.invoke(bean, valueToInject);
            }
        }
    }

    private void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        }
        if (Modifier.isFinal(mod)) {
            if (m instanceof Field field) {
                throw new BeanDefinitionException("Cannot inject final field: " + field);
            }
            if (m instanceof Method) {
                logger.warn(
                        "Inject final method should be careful because it is not called on target bean when bean is proxied and may cause NullPointerException.");

            }
        }
    }

    private void callMethod(Object beanInstance, Method method, String methodName) {
        if (method != null) {
            try {
                method.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if (methodName != null) {
            // Find the method in real class instead of the declared class
            Method foundMethod = ClassUtils.getNamedMethod(beanInstance.getClass(), methodName);
            foundMethod.setAccessible(true);
            try {
                foundMethod.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    // BeanPostProcessor
    // -----------------
    boolean isBeanPostProcessorDefinition(BeanDefinition def) {
        return BeanPostProcessor.class.isAssignableFrom(def.getBeanClass());
    }

    /**
     * Get origin instance
     */
    private Object getOriginInstance(BeanDefinition def) {
        Object beanInstance = def.getInstance();

        // find the origin bean
        List<BeanPostProcessor> reversedBeanPostProcessors = new ArrayList<>(this.beanPostProcessors);
        Collections.reverse(reversedBeanPostProcessors);
        for (BeanPostProcessor processor : reversedBeanPostProcessors) {
            Object restoredInstance = processor.postProcessOnSetProperty(beanInstance, def.getName());
            if (restoredInstance != beanInstance) {
                logger.atDebug().log("BeanPostProcessor {} specified injection from {} to {}.",
                        processor.getClass().getSimpleName(),
                        beanInstance.getClass().getSimpleName(), restoredInstance.getClass().getSimpleName());
                beanInstance = restoredInstance;
            }
        }
        return beanInstance;
    }
}
