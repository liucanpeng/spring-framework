package cn.haitaoss;


import cn.haitaoss.javaconfig.Scope.ScopeTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {
        // ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("spring.xml");
        // AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.Test.class);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));

        ScopeTest.Demo d1 = context.getBean(ScopeTest.Demo.class);
        System.out.println(d1.hashCode() + "--->");

        ScopeTest.Demo d2 = context.getBean(ScopeTest.Demo.class);
        System.out.println(d2.hashCode() + "--->");

        ScopeTest.HaitaoScope.refresh();

        ScopeTest.Demo d3 = context.getBean(ScopeTest.Demo.class);
        System.out.println(d3.hashCode() + "--->");

        /**
         依赖解析忽略
         此部分设置哪些接口在进行依赖注入的时候应该被忽略:

         beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
         beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
         beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
         beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
         beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
         bean伪装
         有些对象并不在BeanFactory中，但是我们依然想让它们可以被装配，这就需要伪装一下:

         beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
         beanFactory.registerResolvableDependency(ResourceLoader.class, this);
         beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
         beanFactory.registerResolvableDependency(ApplicationContext.class, this);
         伪装关系保存在一个Map<Class<?>, Object>里。
         * */
    }
}


