package cn.haitaoss;

import com.sun.tools.attach.VirtualMachine;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.DefaultAdvisorAdapterRegistry;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.ProxyTransactionManagementConfiguration;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author haitao.chen
 * <p>
 * <p>
 * email haitaoss@aliyun.com
 * date 2022-09-24 12:53
 */
@Data
public class Demo {


    @Test
    public void test_agent() throws Exception {
        // 通过目标Java程序的PID 建立拿到其jvm实例
        VirtualMachine virtualMachine = VirtualMachine.attach("PID");
        // 使用jvm实例动态的加载agent
        virtualMachine.loadAgentPath("/path/agent.jar", "args");
        // 断开连接
        virtualMachine.detach();
    }

    private String name;

    @Test
    public void test_write_class() {
        // 输出加载的字节码到本地文件中
        try (
                InputStream inputStream = Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("cn/haitaoss/Demo.class")
                        .openStream();
                FileOutputStream fos = new FileOutputStream("/Users/haitao/Desktop/xx/x.class")
        ) {
            byte[] buff = new byte[102400];
            inputStream.read(buff);
            fos.write(buff);
        } catch (Exception e) {
        } finally {
        }

    }

    @Test
    public void test_() {
        Consumer<Class> consumer = clazz -> System.out.println(
                AnnotatedElementUtils.hasAnnotation(ProxyTransactionManagementConfiguration.class, clazz));
        consumer.accept(Bean.class);
        consumer.accept(Configuration.class);

        // clazz.isAnnotationPresent(this.annotationType)
    }

    public void dynamic_args(String... args) {
        System.out.println("args = " + args);
    }

    @Test
    public void test_dynamic_args() {
        dynamic_args("1");
        dynamic_args("1");
    }

    @Test
    public void test_StringTokenizer() {
        String expression = "(11 1)&(bbb)|c2|!axx";
        StringTokenizer tokens = new StringTokenizer(expression, "()&|!", true);

        while (tokens.hasMoreTokens()) {
            // 返回的是 分隔符 分隔符之间的内容
            String token = tokens.nextToken()
                    .trim();
            System.out.println("token = " + token);
        }
        System.out.println("=====================================");
        System.out.println(Arrays.toString(expression.split("(|)|!|&|\\|")));
    }

    public static void x() {
        System.out.println("execute x...");
    }

    @Test
    public void test_system_out() {
        System.err.println("error...");
        System.out.println("out...");
        System.err.println("error...");
    }

    @Test
    public void test1_reflection() throws NoSuchMethodException {
        System.out.println(Demo.class.getMethod("a")
                .getReturnType()
                .isAssignableFrom(BeanPostProcessor.class));
    }

    public BeanPostProcessor a() {
        return null;
    }

    @Test
    public void test_reflection() throws Exception {

        /*for (Method method : Demo.class.getMethods()) {
            System.out.println(method.getName());
        }*/
        Method x = Demo.class.getMethod("x");
        x.invoke(null);
        x.invoke(Demo.class);
        x.invoke(new Demo());
    }

    public static void main(String[] args) {
        Demo demo = new Demo();
        System.out.println(demo.getName());
        demo.test2();
        Optional.ofNullable(null)
                .ifPresent(item -> {
                });
    }

    @Test
    public void test_compoentType() {
        Object[] objects = {};
        System.out.println(objects.getClass()
                .getComponentType());

        String[] strings = {};
        System.out.println(strings.getClass()
                .getComponentType());


        System.out.println(Array.newInstance(String.class, 0)
                .getClass());
    }

    @Test
    public void test构造器工具类() {
        Constructor<Demo> primaryConstructor = BeanUtils.findPrimaryConstructor(Demo.class);
        System.out.println("primaryConstructor = " + primaryConstructor);
    }

    interface interfaceA<T> {
    }

    @Test
    public void test泛型接口工具() {
        {
            class A<T> {
            }
            class B extends A<String> implements interfaceA<Integer> {
            }

            Class<?> t1 = GenericTypeResolver.resolveTypeArgument(B.class, A.class);
            System.out.println(t1);

            Class<?> t2 = GenericTypeResolver.resolveTypeArgument(B.class, interfaceA.class);
            System.out.println(t2);
        }
    }

    @Test
    public void 测试ProxyFactory() {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(new Demo());
        // 是否优化，这个也是决定是否使用cglib代理的条件之一
        proxyFactory.setOptimize(true);
        // 接口类型，这个也是决定是否使用cglib代理的条件之一，代理接口的时候才需要设置这个
        proxyFactory.setInterfaces();
        // 约束是否使用cglib代理。但是这个没吊用，会有其他参数一起判断的，而且有优化机制 会优先选择cglib代理
        proxyFactory.setProxyTargetClass(true);
        /**
         * addAdvice 会被装饰成 Advisor
         * 这里不能乱写，因为后面解析的时候 要判断是否实现xx接口的
         * 解析逻辑 {@link DefaultAdvisorAdapterRegistry#getInterceptors(Advisor)}
         * */
        proxyFactory.addAdvice(new MethodBeforeAdvice() {
            @Override
            public void before(Method method, Object[] args, Object target) throws Throwable {
                method.invoke(target, args);
            }
        });
        // 设置 Advisor，有点麻烦 还不如直接通过 addAdvice 设置，自动解析成advisor方便
        proxyFactory.addAdvisor(new DefaultPointcutAdvisor(new MethodBeforeAdvice() {
            @Override
            public void before(Method method, Object[] args, Object target) throws Throwable {
                method.invoke(target, args);
            }
        }));
        proxyFactory.getProxy();
    }

    @Test
    public void test1() {
        /**
         * 类名::实例方法
         * --> 匿名内部类
         * class $X{
         *  method(类的实例,...)
         * }
         * */
        Function<Demo, ?> f = Demo::getName;
        BiConsumer<Demo, String> bc = Demo::setName;
    }

    @Test
    public void test2() {
        MyConsumer<String> x = this::test2;
        /*Optional.of("hahah")
                // .ifPresent(x::wrapperConsumer);
                .ifPresent(x::wrapperConsumer2);
*/
        Consumer<String> x2 = x::wrapperConsumer2;// 这个是支持的，返回值不需要而已;
        Consumer<String> x3 = x::wrapperConsumer3;

        Function<String, ?> f = x::wrapperConsumer2;
    }

    public void test2(String s) throws Exception {
        System.out.println(s);
    }

}

interface MyConsumer<T> {
    void accept(T t) throws Exception;

    default void wrapperConsumer(T t) {
        try {
            accept(t);
        } catch (Exception ignored) {

        }

    }

    default Consumer<T> wrapperConsumer2(T t) {
        System.out.println("--->" + t);
        return item -> {
            try {
                accept(t);
            } catch (Exception ignored) {

            }
        };
    }

    default Function<T, T> wrapperConsumer3(T t) {
        System.out.println("--->" + t);
        return item -> t;
    }
}
