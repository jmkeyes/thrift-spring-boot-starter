package io.github.jmkeyes.spring.boot.thrift.server

import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

/**
 * Annotation to enable Thrift controllers. Will scan the package of
 * the annotated configuration class for Thrift controllers by default.
 */
@Inherited
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Import(ThriftControllerRegistrar::class)
annotation class EnableThriftController (
        /**
         * An alias for the "basePackages" attribute.
         *
         * @return See [.basePackages]
         */
        @get:AliasFor("basePackages")
        vararg val value: String = [],

        /**
         * Base packages to scan for annotated classes.
         *
         * @return An array of package names to scan.
         */
        @get:AliasFor("value")
        val basePackages: Array<String> = [],

        /**
         * Type-safe alternative to [.basePackages] for specifying the packages to scan for annotated classes.
         *
         * @return An array of [Class] instances for locating packages to scan.
         */
        val basePackageClasses: Array<KClass<*>> = []
)
