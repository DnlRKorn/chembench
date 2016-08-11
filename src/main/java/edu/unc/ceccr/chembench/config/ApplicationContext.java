package edu.unc.ceccr.chembench.config;

import org.springframework.context.annotation.*;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@Configuration
@ComponentScan("edu.unc.ceccr.chembench")
@Import(PersistenceContext.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableSpringConfigured
public class ApplicationContext {

    @Bean
    static PropertySourcesPlaceholderConfigurer propertyPlaceHolderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Configuration
    @PropertySource("classpath:application.properties")
    static class ApplicationProperties {
    }
}
