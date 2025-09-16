package com.kaiyikang.winter.context;

import java.io.ObjectInputFilter.Config;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.annotation.ComponentScan;
import com.kaiyikang.winter.annotation.Configuration;
import com.kaiyikang.winter.annotation.Import;
import com.kaiyikang.winter.annotation.Order;
import com.kaiyikang.winter.annotation.Primary;
import com.kaiyikang.winter.exception.BeanCreationException;
import com.kaiyikang.winter.exception.BeanDefinitionException;
import com.kaiyikang.winter.exception.BeanNotOfRequiredTypeException;
import com.kaiyikang.winter.exception.NoUniqueBeanDefinitionException;
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

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;

        // Scan to obtain the Class type of all Beans
        final Set<String> beanClassNames = scanForClassNames(configClass);

        this.beans = createBeanDefinitions(beanClassNames);
    }

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
                ? new String[] { Config.class.getPackage().getName() }
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

}
