package io.github.jmkeyes.spring.boot.thrift.server

import org.apache.thrift.protocol.TJSONProtocol
import org.apache.thrift.protocol.TProtocolFactory
import java.lang.annotation.Inherited
import kotlin.reflect.KClass

@Inherited
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class ThriftController (
        /**
         * At least one URL mapping under which this Thrift service should be available.
         */
        vararg val value: String = [],

        /**
         * The protocol factory this Thrift service should communicate with.
         */
        val protocolFactory: KClass<out TProtocolFactory> = TJSONProtocol.Factory::class
)
