Apache Thrift Spring Boot Starter
=================================

Provides better integration of the Apache Thrift library with Spring Boot.

Getting Started
----------------

Add the dependency:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.jmkeyes</groupId>
        <artifactId>thrift-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

Enable the scanning of `@ThriftController` annotations:

```java
@SpringBootApplication
@EnableThriftController
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Assuming you have a `MyThriftService` binding:

```java
@ThriftController("/MyThriftService")
public class ExampleController implements ExampleService.Iface {
    @Override
    public void execute() {
        // We're not in Kansas any more!
    }
}
```

Contributing
------------

  1. Clone this repository.
  2. Create your branch: `git checkout -b feature/branch`
  3. Commit your changes: `git commit -am "I am developer."`
  4. Push your changes: `git push origin feature/branch`
  5. Create a PR of your branch against the `main` branch.
