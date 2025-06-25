Development Notes
=================

Updating Thrift Bindings Manually
---------------------------------

To update the generated Thrift code used in the tests:

```
$ thrift -out src/test/java/ \
    --gen java:beans,jakarta_annotations,generated_annotations=undated  \
    src/test/thrift/ExampleService.thrift
```

This should replace the `ExampleService.java` in the test code.

This is the recommended method. Just don't use the Maven plugin.

Using Maven Thrift Plugin (NOT RECOMMENDED)
-------------------------------------------

I experimented with the upstream Maven "thrift" plugin. I have thoughts:

  - The versions of the Thrift compiler and libraries must be in sync.
  - The plugin is apparently unmaintained and has security problems.
  - The Maven plugin is usable so long as you freeze everything.

Here's what I used in the `pom.xml` to generate the bindings on-demand:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.thrift</groupId>
            <artifactId>thrift-maven-plugin</artifactId>
            <version>0.10.0</version>
            <executions>
                <execution>
                    <id>test-bindings</id>
                    <phase>generate-test-sources</phase>
                    <goals>
                        <goal>testCompile</goal>
                    </goals>
                    <configuration>
                        <generator>java:beans,jakarta_annotations,generated_annotations=undated</generator>
                        <outputDirectory>${basedir}/src/test/java</outputDirectory>
                        <thriftTestSourceRoot>${basedir}/src/test/thrift</thriftTestSourceRoot>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

You'll need to update the GitHub Actions workflows to install a Thrift compiler as well:

```
env:
  # Ubuntu's Noble's latest version.
  THRIFT_VERSION: 0.19.0-2.1build5

jobs:
  build:
    steps:
    - name: Install Thrift Compiler ${THRIFT_VERSION}
      run: |
        sudo apt-get update
        sudo apt-get -y install thrift-compiler=${THRIFT_VERSION}
```

You'll also have to lock the `libthrift` version in `pom.xml` to match.

Overall rating is 1/10: it turns your codebase into a superfund site.
