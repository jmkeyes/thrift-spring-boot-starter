package io.github.jmkeyes.spring.boot.thrift.server;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Annotation to enable Thrift controllers. Will scan the package of
 * the annotated configuration class for Thrift controllers by default.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({ThriftControllerRegistrar.class})
public @interface EnableThriftController {
    /**
     * An alias for the "basePackages" attribute.
     *
     * @return See {@link #basePackages()}
     */
    @AliasFor("basePackages")
    String[] value() default {};

    /**
     * Base packages to scan for annotated classes.
     *
     * @return An array of package names to scan.
     */
    @AliasFor("value")
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to scan for annotated classes.
     *
     * @return An array of {@link Class} instances for locating packages to scan.
     */
    Class<?>[] basePackageClasses() default {};
}
