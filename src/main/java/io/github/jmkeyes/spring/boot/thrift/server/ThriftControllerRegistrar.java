package io.github.jmkeyes.spring.boot.thrift.server;

import lombok.NoArgsConstructor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServlet;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Stream;

/*
 * Given the following Thrift handler ("controller") class:
 *
 *     @ThriftController("/thrift")
 *     public class ExampleController implements ExampleService.Iface {
 *         public void execute() {
 *             // We're not in Kansas any more!
 *         }
 *     }
 *
 * To remove the annotation and be completely magic-free:
 *
 *     @Bean
 *     public ExampleController exampleController() {
 *         return new ExampleController();
 *     }
 *
 *     @Bean
 *     public TProtocolFactory thriftProtocolFactory() {
 *         return new TJSONProtocol.Factory();
 *     }
 *
 *     @Bean
 *     public TServlet thriftControllerServlet(TProtocolFactory protocolFactory, ExampleController handler) {
 *         final TProcessor processor = new ExampleService.Processor<>(handler);
 *         return new TServlet(processor, protocolFactory);
 *     }
 *
 *     @Bean
 *     public ServletRegistrationBean thriftServletRegistration(TServlet thriftControllerServlet) {
 *         ServletRegistrationBean registration = new ServletRegistrationBean<>(thriftControllerServlet);
 *         registration.setName(ExampleController.class.getSimpleName() + "Example");
 *         registration.setUrlMappings("/thrift");
 *         registration.setLoadOnStartup(1);
 *         return registration;
 *     }
 *
 * Don't forget to repeat this process for every Thrift handler you have for magic-free software. :)
 */

/**
 * The {@link ThriftControllerRegistrar} will scan the classpath for all classes annotated with
 * a {@link ThriftController} annotation that also implement a Thrift service/handler interface.
 *
 * For each matching class it will create a {@link ServletRegistrationBean} of a {@link TServlet}
 * that Spring will detect and mount at the path requested by the annotation's value() attribute.
 *
 * This is purely syntax sugar for defining Thrift servlet instances so I don't have to do it.
 */
@NoArgsConstructor
public class ThriftControllerRegistrar implements ImportBeanDefinitionRegistrar {
    /**
     * This is a common {@link ClassLoader} for all class-loading requirements.
     */
    private final ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

    /**
     * When imported with an {@link Import} annotation this registrar will scan the classpath for any and
     * all {@link ThriftController} annotated bean classes and register {@link BeanDefinition} instances that
     * create {@link ServletRegistrationBean} instances that should be loaded on application startup.
     *
     * @param metadata The {@link AnnotationMetadata} of the bean importing the registrar.
     * @param registry The {@link BeanDefinitionRegistry} containing all bean definitions.
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Assert.notNull(metadata, "AnnotationMetadata must not be null!");
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null!");

        // Create a classpath scanner to search for all classes annotated with @ThriftController.
        final AnnotationScanner<ThriftController> scanner = new AnnotationScanner<>(ThriftController.class);

        // Compute a list of all potential base packages to scan including where @EnableThriftController is.
        final List<String> scanPackages = getBasePackages(metadata);

        // Scan the classpath for @ThriftController annotated classes as GenericBeanDefinitions.
        scanner.scan(scanPackages).forEach(definition -> {
            // Resolve a class definition from the class with the @ThriftController annotation.
            final String beanClassName = Objects.requireNonNull(definition.getBeanClassName());
            final Class<?> beanClass = ClassUtils.resolveClassName(beanClassName, classLoader);

            // Generate a bean name for this bean definition using the built-in generator.
            final String beanName = BeanDefinitionReaderUtils.generateBeanName(definition, registry);

            // Override the BeanDefinition to supply a ServletRegistrationBean with our supplier.
            // TODO: This overwrites the original definition; maybe we should use a factory instead?
            definition.setBeanClass(ServletRegistrationBean.class);
            definition.setInstanceSupplier(() -> generateServletRegistration(beanClass));

            // Register this bean definition with the BeanDefinitionRegistry.
            registry.registerBeanDefinition(beanName, definition);
        });
    }

    /**
     * Creates a list of base packages to be scanned for {@link ThriftController} annotated classes.
     *
     * @param metadata The {@link AnnotationMetadata} of the bean importing the registrar.
     * @return A list of base packages as strings.
     */
    private List<String> getBasePackages(AnnotationMetadata metadata) {
        // Collect details from the @EnableThriftController annotation.
        final Class<EnableThriftController> annotation = EnableThriftController.class;

        // Find the attributes of the @EnableThriftController annotation on the importing class.
        final Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(annotation.getName());
        final AnnotationAttributes attributes = new AnnotationAttributes(annotationAttributes);

        // Collect any and all base package names from any of the sources.
        final String[] value = attributes.getStringArray("value");
        final String[] basePackages = attributes.getStringArray("basePackages");
        final Class<?>[] basePackageClasses = attributes.getClassArray("basePackageClasses");

        if (value.length == 0 && basePackages.length == 0 && basePackageClasses.length == 0) {
            // Compute the package for the class that imported this registrar.
            final String basePackage = ClassUtils.getPackageName(metadata.getClassName());

            // Return a single-element list of the package name.
            return Collections.singletonList(basePackage);
        } else {
            // Start a list of base packages to scan.
            final List<String> packages = new ArrayList<>();

            // Add everything from "value" and "basePackages".
            packages.addAll(Arrays.asList(value));
            packages.addAll(Arrays.asList(basePackages));

            // Compute the package name for each individual base package class and add that to the list.
            Arrays.stream(basePackageClasses).map(ClassUtils::getPackageName).forEach(packages::add);

            return packages;
        }
    }

    /**
     * Generates a {@link ServletRegistrationBean} from a class annotated with {@link ThriftController}.
     *
     * @param beanClass The {@link ThriftController} annotated class implementing a Thrift interface.
     * @return A {@link ServletRegistrationBean} for the given bean class.
     */
    private ServletRegistrationBean<?> generateServletRegistration(Class<?> beanClass) {
        // Find all the interfaces implemented by this controller class.
        final Set<Class<?>> interfaces = ClassUtils.getAllInterfacesForClassAsSet(beanClass, classLoader);

        final Class<?> interfaceClass = ThriftClassUtils.getInterfaceClass(interfaces)
                .orElseThrow(() -> new BeanCreationException("No Thrift interface class available!"));

        final Class<?> serviceClass = ThriftClassUtils.getServiceClass(interfaceClass)
                .orElseThrow(() -> new BeanCreationException("No Thrift service class available!"));

        final Class<TProcessor> processorClass = ThriftClassUtils.getProcessorClass(serviceClass)
                .orElseThrow(() -> new BeanCreationException("No Thrift processor class available!"));

        try {
            // Finally create an instance of the controller bean itself.
            final Object handler = BeanUtils.instantiateClass(beanClass);

            // Create a Spring AOP ProxyFactory so we can intercept and "advise" methods on the controller.
            final ProxyFactory proxyFactory = new ProxyFactory(interfaceClass, new SingletonTargetSource(handler));
            proxyFactory.setOptimize(true);
            proxyFactory.setFrozen(true);

            // Find the Thrift protocol factory registered as a bean with this application context.
            final TProtocolFactory protocolFactory = getThriftProtocolFactory(beanClass);

            // Create a new Thrift processor instance from the Thrift interface and processor classes.
            final Constructor<TProcessor> constructor = processorClass.getConstructor(interfaceClass);
            final TProcessor processor = BeanUtils.instantiateClass(constructor, proxyFactory.getProxy());

            // Build a TServlet from the processor and protocol factory.
            final TServlet servlet = new TServlet(processor, protocolFactory);

            // Create a ServletRegistrationBean for the newly created TServlet.
            final ServletRegistrationBean<?> registration = new ServletRegistrationBean<>(servlet);
            registration.setUrlMappings(findServletMappings(beanClass));
            registration.setName(beanClass.getSimpleName() + "Servlet");
            registration.setLoadOnStartup(1);
            return registration;
        } catch (Exception e) {
            throw new BeanCreationException("Couldn't build ServletRegistrationBean", e);
        }
    }

    /**
     * Creates a Thrift protocol factory for the Thrift service under construction.
     *
     * @param beanClass The resolved name of the bean class itself.
     * @return A {@link TProtocolFactory} for the Thrift service.
     */
    private TProtocolFactory getThriftProtocolFactory(Class<?> beanClass) {
        final ThriftController annotation = AnnotationUtils.findAnnotation(beanClass, ThriftController.class);

        if (annotation == null) {
            throw new BeanCreationException("Cannot retrieve annotation on bean class " + beanClass.getSimpleName());
        }

        try {
            return annotation.protocolFactory().getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException("Cannot construct Thrift protocol factory.", e);
        }
    }

    /**
     * Provides the URL mappings that a given bean class should be available under. This will use
     * the {@link ThriftController} annotation's value() attribute on the bean class. If that was
     * not provided, it will fall back to using the short name of the bean class directly.
     *
     * @param beanClass The resolved name of the bean class itself.
     * @return A non-zero length array of strings to mount the servlet under.
     */
    private List<String> findServletMappings(Class<?> beanClass) {
        final ThriftController annotation = AnnotationUtils.findAnnotation(beanClass, ThriftController.class);

        // If the annotation was provided use it's value(s) for the servlet mapping.
        if (annotation != null && annotation.value().length > 0) {
            return Arrays.asList(annotation.value());
        }

        // Use the fallback URL path of "/{ClassName}" if that didn't work out.
        final String fallbackUrlMapping = "/" + beanClass.getSimpleName();

        return Collections.singletonList(fallbackUrlMapping);
    }

    /**
     * Scans the classpath for any class annotated with a specific annotation.
     *
     * @param <T> Any {@link Annotation} class to search the classpath for.
     */
    private static class AnnotationScanner<T extends Annotation> {
        private final ClassPathScanningCandidateComponentProvider scanner;

        /**
         * Builds a new {@link AnnotationScanner} tailored for a specific annotation.
         *
         * @param annotation The {@link Annotation} to scan the classpath for.
         */
        AnnotationScanner(final Class<T> annotation) {
            this.scanner = new ClassPathScanningCandidateComponentProvider(false);
            this.scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        }

        /**
         * Scans a given package(s) for any usage of the {@link Annotation}.
         *
         * @param packages A {@link Collection} of package names to scan.
         * @return A {@link Stream} of {@link BeanDefinition} instances.
         */
        Stream<GenericBeanDefinition> scan(final Collection<String> packages) {
            return packages.stream()
                    .map(scanner::findCandidateComponents)
                    .flatMap(Set::stream)
                    .map(GenericBeanDefinition::new);
        }
    }

    /**
     * Provides a few convenience methods for navigating the generated Thrift class structure.
     */
    private static final class ThriftClassUtils {
        /**
         * Find a Thrift service class given a simple Thrift interface class.
         * <p>
         * In concrete terms: given "MyService.Iface" return "MyService".
         *
         * @param interfaceClass A Thrift interface class.
         * @return A Thrift service class.
         */
        static Optional<Class<?>> getServiceClass(final Class<?> interfaceClass) {
            return Optional.ofNullable(interfaceClass.getDeclaringClass());
        }

        /**
         * Find the Thrift interface class from a provided set of interfaces.
         * <p>
         * In concrete terms: given "implements MyService.Iface, SomeThingElse { ... }" returns "MyService.Iface".
         *
         * @param interfaces A {@link Set} of interfaces to search.
         * @return A Thrift interface class.
         */
        static Optional<Class<?>> getInterfaceClass(final Set<Class<?>> interfaces) {
            return interfaces.stream()
                    .filter(cls -> cls.getName().endsWith("$Iface"))
                    .filter(cls -> cls.getDeclaringClass() != null)
                    .findFirst();
        }

        /**
         * Find the Thrift processor class declared within the service class.
         * <p>
         * In concrete terms: given "MyService" find "MyService.Processor".
         *
         * @param serviceClass A Thrift "service" class.
         * @return A Thrift processor class.
         */
        @SuppressWarnings("unchecked")
        static Optional<Class<TProcessor>> getProcessorClass(final Class<?> serviceClass) {
            return Stream.of(serviceClass.getDeclaredClasses())
                    .filter(cls -> cls.getName().endsWith("$Processor"))
                    .filter(TProcessor.class::isAssignableFrom)
                    .map(cls -> (Class<TProcessor>) cls)
                    .findFirst();
        }
    }
}
