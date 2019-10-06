package io.github.jmkeyes.spring.boot.thrift.server

import org.apache.thrift.TProcessor
import org.apache.thrift.protocol.TProtocolFactory
import org.apache.thrift.server.TServlet
import org.springframework.aop.framework.ProxyFactory
import org.springframework.aop.target.SingletonTargetSource
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.annotation.AnnotationAttributes
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.util.Assert
import org.springframework.util.ClassUtils
import java.util.*
import java.util.stream.Stream

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
 * The [ThriftControllerRegistrar] will scan the classpath for all classes annotated with
 * a [ThriftController] annotation that also implement a Thrift service/handler interface.
 *
 * For each matching class it will create a [ServletRegistrationBean] of a [TServlet]
 * that Spring will detect and mount at the path requested by the annotation's value() attribute.
 *
 * This is purely syntax sugar for defining Thrift servlet instances so I don't have to do it.
 */
class ThriftControllerRegistrar : ImportBeanDefinitionRegistrar {
    /**
     * This is a common [ClassLoader] for all class-loading requirements.
     */
    private val classLoader = ClassUtils.getDefaultClassLoader()

    /**
     * When imported with an [Import] annotation this registrar will scan the classpath for any and
     * all [ThriftController] annotated bean classes and register [BeanDefinition] instances that
     * create [ServletRegistrationBean] instances that should be loaded on application startup.
     *
     * @param metadata The [AnnotationMetadata] of the bean importing the registrar.
     * @param registry The [BeanDefinitionRegistry] containing all bean definitions.
     */
    override fun registerBeanDefinitions(metadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        Assert.notNull(metadata, "AnnotationMetadata must not be null!")
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null!")

        // Create a classpath scanner to search for all classes annotated with @ThriftController.
        val scanner = AnnotationScanner(ThriftController::class.java)

        // Compute a list of all potential base packages to scan including where @EnableThriftController is.
        val scanPackages = getBasePackages(metadata)

        // Scan the classpath for @ThriftController annotated classes as GenericBeanDefinitions.
        scanner.scan(scanPackages).forEach { definition ->
            // Resolve a class definition from the class with the @ThriftController annotation.
            val beanClass = ClassUtils.resolveClassName(definition.beanClassName!!, classLoader)

            // Generate a bean name for this bean definition using the built-in generator.
            val beanName = BeanDefinitionReaderUtils.generateBeanName(definition, registry)

            // Override the BeanDefinition to supply a ServletRegistrationBean with our supplier.
            // TODO: This overwrites the original definition; maybe we should use a factory instead?
            definition.beanClass = ServletRegistrationBean::class.java
            definition.setInstanceSupplier { generateServletRegistration(beanClass) }

            // Register this bean definition with the BeanDefinitionRegistry.
            registry.registerBeanDefinition(beanName, definition)
        }
    }

    /**
     * Creates a list of base packages to be scanned for [ThriftController] annotated classes.
     *
     * @param metadata The [AnnotationMetadata] of the bean importing the registrar.
     * @return A list of base packages as strings.
     */
    private fun getBasePackages(metadata: AnnotationMetadata): List<String> {
        // Collect details from the @EnableThriftController annotation.
        val annotation = EnableThriftController::class.java

        // Find the attributes of the @EnableThriftController annotation on the importing class.
        val annotationAttributes = metadata.getAnnotationAttributes(annotation.name)
        val attributes = AnnotationAttributes(annotationAttributes!!)

        // Collect any and all base package names from any of the sources.
        val value = attributes.getStringArray("value")
        val basePackages = attributes.getStringArray("basePackages")
        val basePackageClasses = attributes.getClassArray("basePackageClasses")

        if (value.isEmpty() && basePackages.isEmpty() && basePackageClasses.isEmpty()) {
            // Return the package name of the class that that imported this registrar.
            return listOf(ClassUtils.getPackageName(metadata.className))
        } else {
            // Start a list of base packages to scan.
            val packages = mutableListOf<String>()

            // Add everything from "value" and "basePackages".
            packages.addAll(listOf(*value))
            packages.addAll(listOf(*basePackages))

            // Compute the package name for each individual base package class and add that to the list.
            packages.addAll(packages.map { ClassUtils.getPackageName(it) })

            return packages
        }
    }

    /**
     * Generates a [ServletRegistrationBean] from a class annotated with [ThriftController].
     *
     * @param beanClass The [ThriftController] annotated class implementing a Thrift interface.
     * @return A [ServletRegistrationBean] for the given bean class.
     */
    private fun generateServletRegistration(beanClass: Class<*>): ServletRegistrationBean<*> {
        // Find all the interfaces implemented by this controller class.
        val interfaces = ClassUtils.getAllInterfacesForClassAsSet(beanClass, classLoader)

        val interfaceClass = ThriftClassUtils.getInterfaceClass(interfaces)
                .orElseThrow { BeanCreationException("No Thrift interface class available!") }

        val serviceClass = ThriftClassUtils.getServiceClass(interfaceClass)
                .orElseThrow { BeanCreationException("No Thrift service class available!") }

        val processorClass = ThriftClassUtils.getProcessorClass(serviceClass)
                .orElseThrow { BeanCreationException("No Thrift processor class available!") }

        try {
            // Finally create an instance of the controller bean itself.
            val handler = BeanUtils.instantiateClass(beanClass)

            // Create a Spring AOP ProxyFactory so we can intercept and "advise" methods on the controller.
            val proxyFactory = ProxyFactory(interfaceClass, SingletonTargetSource(handler))
            proxyFactory.isOptimize = true
            proxyFactory.isFrozen = true

            // Find the Thrift protocol factory registered as a bean with this application context.
            val protocolFactory = getThriftProtocolFactory(beanClass)

            // Create a new Thrift processor instance from the Thrift interface and processor classes.
            val constructor = processorClass.getConstructor(interfaceClass)
            val processor = BeanUtils.instantiateClass(constructor, proxyFactory.proxy)

            // Build a TServlet from the processor and protocol factory.
            val servlet = TServlet(processor, protocolFactory)

            // Create a ServletRegistrationBean for the newly created TServlet.
            val registration = ServletRegistrationBean(servlet)
            registration.urlMappings = findServletMappings(beanClass)
            registration.setName(beanClass.simpleName + "Servlet")
            registration.setLoadOnStartup(1)
            return registration
        } catch (e: Exception) {
            throw BeanCreationException("Couldn't build ServletRegistrationBean", e)
        }

    }

    /**
     * Creates a Thrift protocol factory for the Thrift service under construction.
     *
     * @param beanClass The resolved name of the bean class itself.
     * @return A [TProtocolFactory] for the Thrift service.
     */
    private fun getThriftProtocolFactory(beanClass: Class<*>): TProtocolFactory? {
        val annotation = AnnotationUtils.findAnnotation(beanClass, ThriftController::class.java)
                ?: throw BeanCreationException("Cannot retrieve annotation on bean class " + beanClass.simpleName)

        try {
            return annotation.protocolFactory.objectInstance
        } catch (e: ReflectiveOperationException) {
            throw BeanCreationException("Cannot construct Thrift protocol factory.", e)
        }

    }

    /**
     * Provides the URL mappings that a given bean class should be available under. This will use
     * the [ThriftController] annotation's value() attribute on the bean class. If that was
     * not provided, it will fall back to using the short name of the bean class directly.
     *
     * @param beanClass The resolved name of the bean class itself.
     * @return A non-zero length array of strings to mount the servlet under.
     */
    private fun findServletMappings(beanClass: Class<*>): List<String> {
        val annotation = AnnotationUtils.findAnnotation(beanClass, ThriftController::class.java)

        // If the annotation was provided use it's value(s) for the servlet mapping.
        if (annotation != null && annotation.value.isNotEmpty()) {
            return listOf(*annotation.value)
        }

        // Use the fallback URL path of "/{ClassName}" if that didn't work out.
        return listOf("/" + beanClass.simpleName)
    }

    /**
     * Scans the classpath for any class annotated with a specific annotation.
     *
     * @param <T> Any [Annotation] class to search the classpath for.
     */
    private class AnnotationScanner<T : Annotation>(annotation: Class<T>) {
        private val scanner: ClassPathScanningCandidateComponentProvider =
                ClassPathScanningCandidateComponentProvider(false)

        init {
            this.scanner.addIncludeFilter(AnnotationTypeFilter(annotation))
        }

        /**
         * Scans a given package(s) for any usage of the [Annotation].
         *
         * @param packages A [Collection] of package names to scan.
         * @return A [Stream] of [BeanDefinition] instances.
         */
        internal fun scan(packages: Collection<String>): Stream<GenericBeanDefinition> {
            return packages.stream()
                    .map<Set<BeanDefinition>> { scanner.findCandidateComponents(it) }
                    .flatMap { it.stream() }
                    .map { GenericBeanDefinition(it) }
        }
    }

    /**
     * Provides a few convenience methods for navigating the generated Thrift class structure.
     */
    private object ThriftClassUtils {
        /**
         * Find a Thrift service class given a simple Thrift interface class.
         *
         *
         * In concrete terms: given "MyService.Iface" return "MyService".
         *
         * @param interfaceClass A Thrift interface class.
         * @return A Thrift service class.
         */
        internal fun getServiceClass(interfaceClass: Class<*>): Optional<Class<*>> {
            return Optional.ofNullable(interfaceClass.declaringClass)
        }

        /**
         * Find the Thrift interface class from a provided set of interfaces.
         *
         * In concrete terms: given "implements MyService.Iface, SomeThingElse { ... }" returns "MyService.Iface".
         *
         * @param interfaces A [Set] of interfaces to search.
         * @return A Thrift interface class.
         */
        internal fun getInterfaceClass(interfaces: Set<Class<*>>): Optional<Class<*>> {
            return interfaces.stream()
                    .filter { cls -> cls.name.endsWith("\$Iface") }
                    .filter { cls -> cls.declaringClass != null }
                    .findFirst()
        }

        /**
         * Find the Thrift processor class declared within the service class.
         *
         * In concrete terms: given "MyService" find "MyService.Processor".
         *
         * @param serviceClass A Thrift "service" class.
         * @return A Thrift processor class.
         */
        internal fun getProcessorClass(serviceClass: Class<*>): Optional<Class<TProcessor>> {
            return Stream.of(*serviceClass.declaredClasses)
                    .filter { cls -> cls.name.endsWith("\$Processor") }
                    .filter { TProcessor::class.java.isAssignableFrom(it) }
                    .map { cls -> cls as Class<TProcessor> }
                    .findFirst()
        }
    }
}
