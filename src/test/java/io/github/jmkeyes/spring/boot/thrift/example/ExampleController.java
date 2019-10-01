package io.github.jmkeyes.spring.boot.thrift.example;

import io.github.jmkeyes.spring.boot.thrift.server.ThriftController;

@ThriftController("/thrift")
public class ExampleController implements ExampleService.Iface {
    @Override
    public void execute() {
        // We're not in Kansas any more!
    }
}