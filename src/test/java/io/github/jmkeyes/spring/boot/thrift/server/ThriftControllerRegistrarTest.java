package io.github.jmkeyes.spring.boot.thrift.server;

import io.github.jmkeyes.spring.boot.thrift.example.ExampleConfiguration;
import io.github.jmkeyes.spring.boot.thrift.example.ExampleController;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.server.TServlet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.servlet.Servlet;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class ThriftControllerRegistrarTest {
    /**
     * A simple unit test to verify that the registrar operates as designed.
     */
    @Test
    public void testRegistersBeanDefinitionsWhenImported() {
        final AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ExampleConfiguration.class);

        final String beanName = String.format("%s#%d", ExampleController.class.getName(), 0);

        assertTrue(context.containsBeanDefinition(beanName));
        assertTrue(context.containsBean(beanName));

        final BeanDefinition definition = context.getBeanDefinition(beanName);
        assertTrue(definition instanceof GenericBeanDefinition);

        final Object bean = context.getBean(beanName);
        assertTrue(bean instanceof ServletRegistrationBean);

        final ServletRegistrationBean<?> registrationBean = (ServletRegistrationBean<?>) bean;
        assertEquals(registrationBean.getServletName(), "ExampleControllerServlet");
        assertTrue(registrationBean.getUrlMappings().contains("/thrift"));

        final Servlet servlet = registrationBean.getServlet();
        assertTrue(servlet instanceof TServlet);
    }
}
