package io.github.jmkeyes.spring.boot.thrift.server;

import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

import java.lang.annotation.*;

/**
 * An annotation to mark the class as a Thrift service controller.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ThriftController {
    /**
     * At least one URL mapping under which this Thrift service should be available.
     *
     * @return At least one {@link String} path to mount the servlet at.
     */
    String[] value() default {};

    /**
     * The protocol factory this Thrift service should communicate with.
     *
     * @return The {@link TProtocolFactory} this Thrift service should use.
     */
    Class<? extends TProtocolFactory> protocolFactory() default TJSONProtocol.Factory.class;
}
