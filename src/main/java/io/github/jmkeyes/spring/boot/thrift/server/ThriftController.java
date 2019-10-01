package io.github.jmkeyes.spring.boot.thrift.server;

import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ThriftController {
    /**
     * At least one URL mapping under which this Thrift service should be available.
     */
    String[] value() default {};

    /**
     * The protocol factory this Thrift service should communicate with.
     */
    Class<? extends TProtocolFactory> protocolFactory() default TJSONProtocol.Factory.class;
}
