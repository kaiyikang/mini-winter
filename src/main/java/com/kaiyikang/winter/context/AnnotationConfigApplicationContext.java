package com.kaiyikang.winter.context;

import java.io.ObjectInputFilter.Config;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
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

        // Instance other normal beans
        createNormalBeans();

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
                if (autowiredBeanInstance == null && !isConfiguration) {
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

        // --- 5. Save and return ---
        def.setInstance(instance);

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

    @SuppressWarnings("unchecked")
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
}
