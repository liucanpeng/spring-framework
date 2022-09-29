# 源码环境搭建

## 本地编译配置

1. 删掉代码检查规范
2. 添加仓库
3. 注释掉插件
4. 配置环境变量，我配置的是 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.16.jdk/Contents/Home`
5. 编译代码：`./gradlew build`

## IDEA配置

- 需要在idea中配置 gradle 的编译信息

![img.png](.README_imgs/img.png)

- 配置项目的编译器信息

![image-20220727215837291](.README_imgs/image-20220727215837291.png)

- 使用 idea 的 gradle 插件构建失败，可以使用命令行进行构建 `./gradlew build`

## 使用 lombok

build.gradle 
```groovy
dependencies {
    // lombok
    annotationProcessor 'org.projectlombok:lombok:1.18.2'
    compileOnly 'org.projectlombok:lombok:1.18.2'
    testAnnotationProcessor 'org.projectlombok:lombok:1.18.2'
    testCompileOnly 'org.projectlombok:lombok:1.18.2'
}

```

删除警告就报错的编译配置参数：
全局搜索这个参数`-Werror` 注释掉就行了


## junit5 的使用

依赖：
```groovy
dependencies {
    // 测试依赖
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.1'
}
```

IDEA配置：
![img1.png](.README_imgs/img_1.png)
# 源码分析
## [ASM 技术](https://asm.ow2.io/)

> 简单来说，ASM是一个操作Java字节码的类库。
>
> #### 第一个问题，ASM的操作对象是什么呢？
>
> ASM所操作的对象是字节码（ByteCode）数据
>
> #### 第二个问题，ASM是如何处理字节码（ByteCode）数据的？
>
> - ASM处理字节码（ByteCode）的方式是“拆分－修改－合并”
> - SM处理字节码（ByteCode）数据的思路是这样的：第一步，将.class文件拆分成多个部分；第二步，对某一个部分的信息进行修改；第三步，将多个部分重新组织成一个新的.class文件。
> - 说白了就是文件解析，并不会把这个 .class 文件加载到 JVM 中，就是文件的解析工具

为什么要使用 ASM ?

- 扫描到所有的 class 资源后，要判断该 class 是否作为一个 bean对象（比如标注了@Component 注解），
  如果我们通过反射来判断，那么在 Spring 启动阶段就会加载很多的bean，这势必会浪费系统资源和耗时（因为可能很多的工具类，是不需要Spring进行管理的）。
## Spring容器创建的核心流程
```java
/**
 *  `new AnnotationConfigApplicationContext(AppConfig.class)` 会发生什么？？？
 *
 * 1. 执行AnnotationConfigApplicationContext构造器 {@link AnnotationConfigApplicationContext#AnnotationConfigApplicationContext()}
 *      实例化属性reader {@link AnnotatedBeanDefinitionReader#AnnotatedBeanDefinitionReader(BeanDefinitionRegistry)}
 *      reader 属性的构造器里面会注册BeanFactoryPostProcessor {@link AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry)}
 *          默认会注册这五个鬼东西：
 *
 *              1. ConfigurationClassPostProcessor 类型：BeanFactoryPostProcessor
 *                  - 在BeanFactory初始化阶段，会使用该处理器 扫描配置类注册BeanDefinition到IOC容器中
 *
 *              2. CommonAnnotationBeanPostProcessor    类型：BeanPostProcessor
 *
 *              3. AutowiredAnnotationBeanPostProcessor 类型：BeanPostProcessor
 *
 *              4. EventListenerMethodProcessor 类型：BeanFactoryPostProcessor、SmartInitializingSingleton
 *                  - 作为 BeanFactoryPostProcessor 的功能。会存储IOC容器中所有的 EventListenerFactory 类型的bean，作为处理器的属性
 *                  - 作为 SmartInitializingSingleton 的功能。：用来处理 @EventListener 注解的，会在提前实例化单例bean的流程中 回调该实例的方法
 *                      BeanFactory 的{@link DefaultListableBeanFactory#preInstantiateSingletons()}：
 *                          - 创建所有单例bean。
 *                          - 回调 {@link SmartInitializingSingleton#afterSingletonsInstantiated()}
 *
 *              5. DefaultEventListenerFactory 类型：EventListenerFactory
 *                  就是 EventListenerMethodProcessor 解析 @EventListener 的时候会用这个工厂来创建 ApplicationListener
 *
 *  2. 解析入参,添加到BeanDefinitionMap中 {@link AnnotationConfigApplicationContext#register(Class[])}
 *
 *  3. 刷新IOC容器 {@link AbstractApplicationContext#refresh()}
 *
 *      准备刷新 {@link AbstractApplicationContext#prepareRefresh()}
 *          - 初始化属性
 *          - 为IOC容器设置两个属性：applicationListeners、earlyApplicationEvents
 *
 *      获取并刷新BeanFactory {@link AbstractApplicationContext#obtainFreshBeanFactory()}
 *          - 是 AnnotationConfigApplicationContext 类型的IOC容器，直接返回IOC容器的BeanFactory
 *          - 是 ClassPathXmlApplicationContext 类型的IOC容器，会创建新的BeanFactory，并解析xml 注册BeanDefinition 到BeanFactory中
 *
 *      准备BeanFactory {@link AbstractApplicationContext#prepareBeanFactory(ConfigurableListableBeanFactory)}
 *          - 给BeanFactory 的属性设置值：beanClassLoader、beanExpressionResolver、propertyEditorRegistrars、
 *              ignoredDependencyInterfaces、resolvableDependencies、tempClassLoader
 *          - 往BeanFactory注册 BeanPostProcessor
 *              比如 {@link ApplicationContextAwareProcessor},该处理器是处理bean实现了XxAware接口的 {@link ApplicationContextAwareProcessor#postProcessBeforeInitialization(Object, String)}
 *          - 往BeanFactory注册 单例bean，比如 environment
 *
 *      留给子类的模板方法，入参就是准备好的BeanFactory。 {@link AbstractApplicationContext#postProcessBeanFactory(ConfigurableListableBeanFactory)}
 *
 *      执行BeanFactoryPostProcessor,完成BeanFactory的创建 {@link AbstractApplicationContext#invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory)}
 *          先执行 {@link BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(BeanDefinitionRegistry)}
 *          这里会使用while循环保证 如果在postProcessBeanDefinitionRegistry 里面注册了BeanDefinitionRegistryPostProcessor也能执行。
 *          然后再执行 {@link BeanFactoryPostProcessor#postProcessBeanFactory(ConfigurableListableBeanFactory)}
 *
 *          注：解析配置类并注册BeanDefinition就是在这个环节实现的，通过 {@link ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry(BeanDefinitionRegistry)}
 *
 *      注册 BeanPostProcessor。 {@link AbstractApplicationContext#registerBeanPostProcessors(ConfigurableListableBeanFactory)}
 *          其实就是 获取 BeanFactory 的 BeanDefinitionMap 属性中，从属性中获取 BeanPostProcessor 类型的bean，getBean() 创建，
 *          然后添加到 BeanFactory 的beanPostProcessors属性中
 *
 *          注：
 *              - BeanPostProcessor的注册是有序的。优先级：PriorityOrdered 大于 Ordered 大于 nonOrdered 大于 internalPostProcessors(MergedBeanDefinitionPostProcessor类型)
 *              - 最后在添加一个 `beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));`
 *                  该后置处理器有两个作用：
 *                      - bean初始化后阶段：处理的bean 继承 ApplicationListener，那就将当前bean添加到IOC容器的 applicationEventMulticaster 属性中
 *                      - bean销毁阶段：从IOC容器的 ApplicationEventMulticaster 属性移除当前 bean
 *
 *      初始化MessageSource,就是往BeanFactory注册国际化资源解析器。 {@link AbstractApplicationContext#initMessageSource()}
 *
 *      初始化 ApplicationEventMulticaster，就是往BeanFactory注册事件广播器 {@link AbstractApplicationContext#initApplicationEventMulticaster()}
 *
 *      开始刷新，留给子类实现的。{@link AbstractApplicationContext#onRefresh()}
 *          SpringBoot web应用，就是在这里启动的web容器，或者是初始化web容器
 *          注：可以在这里往 {@link AbstractApplicationContext#earlyApplicationEvents} 属性设置值，这样子就可以在下面注册监听器环节发布早期事件，然后对应的
 *              事件监听器就能收到 BeanFactory已经准备好了，可以在事件监听器里面做操作
 *
 *      注册监听器 {@link AbstractApplicationContext#registerListeners()}
 *          - 实例化 BeanFactory 中类型为 ApplicationListener 的bean，然后添加到 BeanFactory 的属性 ApplicationEventMulticaster 中(这个就是在上面穿件的)
 *          - IOC容器的 earlyApplicationEvents 属性不为空，通过 ApplicationEventMulticaster 将时间发布到 ApplicationListener
 *
 *      完成BeanFactory的初始化 {@link AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)}
 *          - 设置 BeanFactory 的一些属性：conversionService、embeddedValueResolvers
 *          - 提前创建 LoadTimeWeaverAware 类型的bean
 *          - 提前实例化单例bean {@link DefaultListableBeanFactory#preInstantiateSingletons()}
 *
 *      完成刷新 {@link AbstractApplicationContext#finishRefresh()}
 *          - 清缓存 {@link DefaultResourceLoader#clearResourceCaches()}
 *          - 初始化LifecycleProcessor，BeanFactory中没有这个bean就注册个默认值，会作为IOC容器的lifecycleProcessor属性 {@link AbstractApplicationContext#initLifecycleProcessor()}
 *          - refresh完成了 传播给 LifecycleProcessor {@link getLifecycleProcessor().onRefresh()}
 *          - 发布 ContextRefreshedEvent 事件  {@link AbstractApplicationContext#publishEvent(ApplicationEvent)}
 * */
```
## BeanFactoryPostProcessor

特点：只会在 IOC 生命周期中 执行一次。就是一个bean工厂执行一次

有两个接口可用：

- BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(
  org.springframework.beans.factory.support.BeanDefinitionRegistry)
    - 可用来修改和注册 BeanDefinition（JavaConfig 就是通过 ConfigurationClassPostProcessor 来解析配置类注册beanDefinition的）
    - 可以在这里面套娃注册 BeanDefinitionRegistryPostProcessor，也可以注册 BeanFactoryPostProcessor。因为底层是使用 while 循环来处理
- BeanFactoryPostProcessor#postProcessBeanFactory(
  org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
    - 此时的参数就是beanFactory，可以注册BeanDefinition、创建Bean
    - 可以在这里创建bean 来实现提前创建的目的，但是会破坏bean的生命周期，因为此时容器中还没有创建出BeanPostProcessor
    - 在这里注册 BeanDefinitionRegistryPostProcessor、BeanFactoryPostProcessor 是没有用的，不会作为BeanFactory 来执行回调方法
## BeanPostProcessor

特点：每个bean的生命周期中都会执行一次。实例化前、构造器初始化、实例化后、属性填充前、初始化前、初始化后

```java
/**
 * 核心的接口类型：
 *  1. SmartInstantiationAwareBeanPostProcessor
 *  2. MergedBeanDefinitionPostProcessor
 *  3. InstantiationAwareBeanPostProcessor
 *  4. BeanPostProcessor
 *  5. DestructionAwareBeanPostProcessor
 *
 * 四处地方可以将对象加工厂代理对象：
 *  1. SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference
 *      - 在这里代理不会出现循环依赖问题
 *  2. InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
 *      - 在这里代理不会出现循环依赖问题
 *  3. BeanPostProcessor#postProcessBeforeInitialization
 *  4. BeanPostProcessor#postProcessAfterInitialization
 *
 *  每个 BeanPostProcessor 回调方法的作用：
 *     提前AOP
 *     org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 *
 *     实例化前。如果该方法返回值不为null，先执行初始化后，然后直接返回该对象。不在执行bean生命周期的构造器初始化、属性填充、初始化操作）
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation(java.lang.Class, java.lang.String)
 *
 *     构造器初始化。如果返回值不为null，就会使用返回的构造器进行实例化
 *     org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors(java.lang.Class, java.lang.String)
 *
 *     合并beanDefinition。这里可以拿到BeanDefinition
 *     org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition(org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Class, java.lang.String)
 *
 *     实例化后。可以拿到构造器初始化后的对象
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation(java.lang.Object, java.lang.String)
 *
 *     属性注入前。可以拿到解析注解或者xml中设置的属性值
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessProperties(org.springframework.beans.PropertyValues, java.lang.Object, java.lang.String)
 *
 *     属性注入前。可以拿到解析注解或者xml中设置的属性值（过时方法）
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessPropertyValues(org.springframework.beans.PropertyValues, java.beans.PropertyDescriptor[], java.lang.Object, java.lang.String)
 *
 *     初始化前。此时的bean已经完成了属性注入、Wrapper注入，还未执行初始化方法(org.springframework.beans.factory.InitializingBean#afterPropertiesSet())
 *     org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization(java.lang.Object, java.lang.String)
 *
 *     初始化后。这是bean生命周期的最后一个环节了
 *     org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 *
 *     销毁bean的回调
 *     org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor#requiresDestruction(java.lang.Object)
 * */
```
## 详解ConfigurationClassPostProcessor

```java
/**
 * 1. BeanDefinitionRegistryPostProcessor 回调
 * {@link org.springframework.context.annotation.ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry(BeanDefinitionRegistry)}
 *
 * 2. 处理方法
 * {@link org.springframework.context.annotation.ConfigurationClassPostProcessor#processConfigBeanDefinitions(BeanDefinitionRegistry)}
 *
 * 3. 校验 ApplicationContext 入参传入的Class 是否是配置类
 *       什么才是配置类？{@link org.springframework.context.annotation.ConfigurationClassUtils#checkConfigurationClassCandidate(BeanDefinition, MetadataReaderFactory)}
 *       有 @Configuration(proxyBeanMethods=true) full配置类
 *       有 @Configuration(proxyBeanMethods=false) lite配置类
 *       无 @Configuration 但是有  (@Component || @ComponentScan || @Import || @ImportResource || @Bean ) lite配置类
 *       都不满足就不是配置类
 *
 *       将配置类添加到集合中 --> configCandidates
 *
 * 4. 对 configCandidates 进行升序排序。可通过 @Order 实现排序
 *
 * 5. 创建解析器
 * {@link org.springframework.context.annotation.ConfigurationClassParser#ConfigurationClassParser(org.springframework.core.type.classreading.MetadataReaderFactory, org.springframework.beans.factory.parsing.ProblemReporter, org.springframework.core.env.Environment, org.springframework.core.io.ResourceLoader, org.springframework.beans.factory.support.BeanNameGenerator, org.springframework.beans.factory.support.BeanDefinitionRegistry)}
 *
 * 6. 使用解析器解析 configCandidates。{@link org.springframework.context.annotation.ConfigurationClassParser#parse(java.util.Set)}
 *      注：解析是有序的。先解析完非 @Import(DeferredImportSelector.class) 的配置类，在解析 @Import(DeferredImportSelector.class)
 *
 *      1. 先解析的内容 @Component、@ComponentScans、@ComponentScan、@Bean、@ImportResource、@Import(非实现DeferredImportSelector.class)
 *          {@link org.springframework.context.annotation.ConfigurationClassParser#processConfigurationClass(org.springframework.context.annotation.ConfigurationClass, java.util.function.Predicate)}
 *          为了处理配置类的父类是配置类的情况，采用 do...while 递归解析 保证所有的内容都能解析完
 *              {@link org.springframework.context.annotation.ConfigurationClassParser#doProcessConfigurationClass(org.springframework.context.annotation.ConfigurationClass, org.springframework.context.annotation.ConfigurationClassParser.SourceClass, java.util.function.Predicate)}
 *                  1. 有@Component 注解，解析成员内部类信息 {@link org.springframework.context.annotation.ConfigurationClassParser#processMemberClasses(org.springframework.context.annotation.ConfigurationClass, org.springframework.context.annotation.ConfigurationClassParser.SourceClass, java.util.function.Predicate)}
 *                  2. 解析 @PropertySource
 *                  3. 对 @ComponentScans、@ComponentScan 解析。
 *                      实例化 Scanner，默认会添加对@Component 解析的 includeFilter {@link ClassPathBeanDefinitionScanner#ClassPathBeanDefinitionScanner(BeanDefinitionRegistry, boolean, Environment, ResourceLoader)}
 *                      {@link org.springframework.context.annotation.ComponentScanAnnotationParser#parse(org.springframework.core.annotation.AnnotationAttributes, java.lang.String)}
 *                      {@link org.springframework.context.annotation.ClassPathBeanDefinitionScanner#doScan(java.lang.String...)}
 *                      3.1 excludeFilter + includeFilter + @Conditional 的校验 {@link org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#isCandidateComponent(org.springframework.core.type.classreading.MetadataReader)}
 *                          注：
 *                      3.2 设置BeanDefinition信息：beanName、@Autowired、@Lazy、@Primary、@DependsOn、@Role、@Description
 *                      3.3 校验是否可以注册到BeanDefinitionMap中 {@link org.springframework.context.annotation.ClassPathBeanDefinitionScanner#checkCandidate(java.lang.String, org.springframework.beans.factory.config.BeanDefinition)}
 *                      3.4 注册 BeanDefinition 到 IOC 容器中{@link ClassPathBeanDefinitionScanner#registerBeanDefinition(BeanDefinitionHolder, BeanDefinitionRegistry)}
 *                      
 *      2. 后解析的内容 @Import(DeferredImportSelector.class)
 *          {@link org.springframework.context.annotation.ConfigurationClassParser.DeferredImportSelectorHandler#process()}
 *              最终还是调用该方法解析配置类 {@link org.springframework.context.annotation.ConfigurationClassParser#processConfigurationClass(org.springframework.context.annotation.ConfigurationClass, java.util.function.Predicate)}
 *
 *      解析完的结果就是 Map<ConfigurationClass, ConfigurationClass> configurationClasses
 *
 * 7. 遍历 configurationClasses，将解析完的配置内容 定义成 BeanDefinition 注册到 BeanDefinitionMap中
 *      {@link org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitions(java.util.Set)}
 *      {@link org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForConfigurationClass(org.springframework.context.annotation.ConfigurationClass, org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator)}
 *          1. 注册 @Import(非BeanDefinitionRegistry.class) 导入的类 {@link ConfigurationClassBeanDefinitionReader#registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass)}
 *          2. 注册 @Bean {@link ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForBeanMethod(BeanMethod)}
 *          3. 注册 @ImportResource {@link ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsFromImportedResources(Map)}
 *          4. 注册 @Import(BeanDefinitionRegistry.class) {@link ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsFromRegistrars(Map)}
 * */
```
## bean 创建的生命周期

```java
/**
 * org.springframework.beans.factory.support.AbstractBeanFactory#getBean(java.lang.String, java.lang.Class, java.lang.Object...)
 * org.springframework.beans.factory.support.AbstractBeanFactory#doGetBean(java.lang.String, java.lang.Class, java.lang.Object[], boolean)
 *   循环依赖核心代码：如果bean正在创建 -> 二级缓存获取 -> 三级缓存 对正在创建的bean 进行提前AOP 然后返回
 *      org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(java.lang.String)
 * org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 * org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 *  org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBeanInstance(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 *  org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, org.springframework.beans.BeanWrapper)
 *  org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#initializeBean(java.lang.String, java.lang.Object, org.springframework.beans.factory.support.RootBeanDefinition)
 * */
```

1. 所有的bean 都是通过 `getBean()` 来创建的

```java
/**
 *
 * @see AbstractApplicationContext#refresh()
 * @see AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)
 * @see DefaultListableBeanFactory#preInstantiateSingletons()
 *  - 遍历 beanNames 
 *  - 不是抽象的 && 是单例的 && 不是懒加载的
 *  - 是不是 FactoryBean :
 *      - 是：
 *          - 创建 FactoryBean 实例
 *          - 判断 是否立即初始化 FactoryBean#getObject 返回的bean
 *      - 不是： 
 *          @see org.springframework.beans.factory.support.AbstractBeanFactory#getBean(java.lang.String)
 *      注：创建bean 都是执行 getBean
 * */
```

2. `getBean()` 流程

```java
/**
 * @see AbstractBeanFactory#doGetBean(String, Class, Object[], boolean)
 * 根据beanName 从单例池获取bean
 *  是否存在
 *      存在：
 *          - beanName 是 &开头的 直接返回
 *          - 不是 &开头，获取的 bean 不是 FactoryBean 的实例 直接返回
 *          - 不是 &开头，获取的 bean 是 FactoryBean 的实例，那就是要返回 FactoryBean#getObject 返回的bean
 *              1. 从 factoryBeanObjectCache 中获取
 *              2. 缓存中不存在，执行 `FactoryBean#getObject` 存储缓存，然后返回
 *      不存在：
 *          - 当前beanFactory 中不存在 该beanName 的 definition，判断父 beanFactory 是否存在，存在就执行  org.springframework.beans.factory.BeanFactory#getBean(java.lang.String)
 *          - 获取该bean 所有的 dependsOn 的值，遍历执行 org.springframework.beans.factory.support.AbstractBeanFactory#getBean(java.lang.String)
 *          - 是单例bean
 *              @see org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(java.lang.String, org.springframework.beans.factory.ObjectFactory)
 *          - 是多例bean
 *              @see org.springframework.beans.factory.support.AbstractBeanFactory#createBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 *          - 其他bean（web应用的：request域、session域、application域）
 *              @see org.springframework.beans.factory.config.Scope#get(java.lang.String, org.springframework.beans.factory.ObjectFactory)
 * */
```

3. `DefaultSingletonBeanRegistry#getSingleton` 流程

```java
/**
 * DefaultSingletonBeanRegistry#getSingleton
 * 1. 从单例缓存池中获取不到 bean `this.singletonObjects.get(beanName);`
 *  - 单例池获取 -> 二级缓存获取 -> 三级缓存获取，执行缓存里的ObjectFactory 进行提前AOP
 *  - 提前AOP，【第一次】执行beanPostProcessor
 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 * 2. 标记当前bean 正在创建（用来解决循环依赖）`this.singletonsCurrentlyInCreation.add(beanName)`
 * 3. 创建bean 
 *  @see AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])
 * 4. 删除标记 `this.singletonsCurrentlyInCreation.remove(beanName)`
 * 5. 将bean 放入单例池
 *  @see DefaultSingletonBeanRegistry#addSingleton(String, Object)
 *  1. 加入到单例缓存池中
 *  2. 从三级缓存中移除
 *  3. 从二级缓存中移除
 * */
```

4. `AbstractAutowireCapableBeanFactory#createBean` 可以通过后置处理器，快速返回，不要执行bean创建的生命周期

```java
/**
 * @see AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])
 * 1. 【第二次】执行beanPostProcessor 可以实现 不执行后面 bean的构造器、属性注入、初始化流程（简而言之可以拦截bean的创建过程）
 *  - 返回值是否为null:
 *      为null：继续走 bean 的生命周期流程
 *      不为null：执行beanPostProcessor，执行bean的初始化后动作（AOP和注解事务是在 `postProcessAfterInitialization` 实现的）
 *          @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation(java.lang.Class, java.lang.String)
 * 2. 真正开始创建bean了，返回创建结果(这里才是bean的核心生命周期流程) 
 * @see org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 * */
```

5. `AbstractAutowireCapableBeanFactory#doCreateBean` bean创建的生命周期

```java
/**
 * 1. 构造器初始化
 *  @see AbstractAutowireCapableBeanFactory#createBeanInstance(String, RootBeanDefinition, Object[])
 *  【第三次】执行beanPostProcessor，返回构造器
 *      @see SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors(Class, String)
 * 2. 【第四次】执行beanPostProcessor, 对 @Autowired @Value的注解的预解析
 *  @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition(RootBeanDefinition, Class, String)
 *  3. 满足（是单例bean && 允许循环引用 && 当前bean在真正创建bean集合中）往三级缓存中，记录 当前实例化的bean 的 提前AOP 操作
 * @see DefaultSingletonBeanRegistry#addSingletonFactory(String, ObjectFactory)
 *      - 参数 ObjectFactory
 *          @see AbstractAutowireCapableBeanFactory#getEarlyBeanReference(String, RootBeanDefinition, Object)
 *          - `getEarlyBeanReference` 里面其实是提前AOP的操作，说白了就是执行beanPostProcessor
 *              @see SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(Object, String)
 *  4. 属性注入
 * @see AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)
 *      1. 【第五次】执行beanPostProcessor, 停止属性注入
 *          @see InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation(Object, String)
 *      2. 对属性的值进行解析(会使用 TypeConverter )，存到 `PropertyValues`（注意这里还没有把值设置到bean对象中，只是存到 `PropertyValues`）
 *          @see AutowireCapableBeanFactory#resolveDependency(DependencyDescriptor, String, Set, TypeConverter)
 *      3. 【第六次】执行beanPostProcessor, 也是解析配置的属性值 记录在 `PropertyValues`
 *          @see InstantiationAwareBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)
 *      4. 【第七次】执行beanPostProcessor，也是解析配置的属性值 记录在 `PropertyValues`
 *          @see InstantiationAwareBeanPostProcessor#postProcessPropertyValues(PropertyValues, PropertyDescriptor[], Object, String)
 *      5. 如果上面解析的 PropertyValues 不为null，就把 `PropertyValues` 注入到 bean实例中，完成属性注入
 *          @see AbstractAutowireCapableBeanFactory#applyPropertyValues(String, BeanDefinition, BeanWrapper, PropertyValues)
 *  5. 初始化操作
 * @see AbstractAutowireCapableBeanFactory#initializeBean(String, Object, RootBeanDefinition)
 *      1. 完成对 XxxAware 接口的方法回调
 *          @see AbstractAutowireCapableBeanFactory#invokeAwareMethods(String, Object)
 *      2. 【第八次】执行beanPostProcessor, 比如 执行 @PostConstruct 标注的方法
 *          @see BeanPostProcessor#postProcessBeforeInitialization(Object, String)
 *      3. 执行初始化方法, 执行 org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
 *          @see AbstractAutowireCapableBeanFactory#invokeInitMethods(String, Object, RootBeanDefinition)
 *      4. 【第九次】执行beanPostProcessor, @EnableAspectJAutoProxy 、@EnableTransactionManagement 都是在这里完成代理对象的创建的
 *          @see BeanPostProcessor#postProcessAfterInitialization(Object, String)
 *  6. 销毁bean
 *      【第十次】执行beanPostProcessor
 *          @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor.requiresDestruction
 * */
```
## 如何实现bean创建的优先级

```java
/**
 * 如何实现bean创建的优先级：
 * 1. 实现 BeanFactoryPostProcessor（缺点：破坏了bean的生命周期）
 * 2. 重写 cn.haitaoss.javaconfig.ordercreatebean.MySmartInstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation(java.lang.Class, java.lang.String)
 *  这个是 BeanPostProcessor 中最先执行的回调方法，其他的BeanPostProcessor 也可以
 * 3. 重写 onRefresh 方法，通过发布并消费早期事件
 *      cn.haitaoss.javaconfig.applicationlistener.MyAnnotationConfigApplicationContext#onRefresh()
 * 4. 使用 @DependsOn("b")
 */
```

通过重写 `onRefresh` 发布早期事件，实现bean的提前创建 示例

```java
public class MyAnnotationConfigApplicationContext extends AnnotationConfigApplicationContext {
    public MyAnnotationConfigApplicationContext(Class<?>... componentClasses) {
        super(componentClasses);
    }

    @Override
    protected void onRefresh() throws BeansException {
        publishEvent(new MyApplicationEvent("beanDefinition 全部加载完了，可以自定义bean加载顺序了") {
            @Override
            public Object getSource() {
                return super.getSource();
            }
        });
    }

    public static void main(String[] args) {
        new MyAnnotationConfigApplicationContext(AppConfig.class);
    }
}

```

```java
@Component
public class MyApplicationListener implements ApplicationListener<MyApplicationEvent>, ApplicationContextAware {
    private ApplicationContext applicationContext;

    public MyApplicationListener() {
        System.out.println("MyApplicationListener....");
    }

    @Override
    public void onApplicationEvent(MyApplicationEvent event) {
        System.out.println("event is : " + event);
        System.out.println("编译 提前加载bean的逻辑");

        applicationContext.getBean("testPreCreate3");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

```
## 单例bean循环依赖导致的错误

```java
/**
 * spring 为了解决单例bean循环依赖问题，是才用 提请 AOP 的方式 来解决的，
 * 提前AOP是执行
 *      @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 * 然后在bean的生命周期的最后阶段会执行 org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 * 也可能会返回代理对象。所以就可能出现 postProcessAfterInitialization 创建的代理对象和一开始提前AOP注入给其他bean的不一样
 * 所以只能报错了。
 *
 * 解决方式：
 * 1. 将 postProcessAfterInitialization 的代理逻辑放到 SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference 实现
 * 2. 使用 @Lazy 注解，不要在初始化的时间就从容器中获取bean，而是直接返回一个代理对象
 * 3. 使用 @Lookup 注解，延迟bean的创建，避免出现循环依赖问题
 */
```
## @Lookup 

### 有啥用？
- 这个注解标注在方法上。
- 如果一个bean对象中的方法标注了 Lookup注解，那么会生成代理对象放入 bean容器中(在是实例化阶段通过后置处理器实现的)。
- 执行代理对象的方法，如果方法是标注了 Lookup 注解的方法时，会直接返回 Lookup 需要查找的bean，并不会执行方法体

使用说明：如果Lookup注解的value没有指定，那么会根据方法的返回值类型查找bean，如果指定了value 那就根据name查找bean

使用场景：A 依赖多例bean B，可以使用Lookup 注解在A中定义一个方法，该方法每次都会从容器中获取一个bean，因为B 是多例的，所以每次都是返回新的对象

@Lookup 使用场景：
```java
@Component
public class LookupService {
    @Autowired
    private Demo demo;

    @Lookup("demo")
    public Demo getDemo() {
        System.out.println("哈哈哈，我是废物");
        return null;
    }

    public void test1() {
        System.out.println(demo); // 单例的，不符合 Demo 这个bean的作用域
    }

    public void test2() {
        System.out.println(getDemo()); // 多例的
    }

}

@Component
@Scope("prototype")
class Demo {

}
```
@Lookup 失效的情况：
```java
@Component
@Data
public class Test {
    @Autowired
    private A a;

    @Lookup("a")
    // @Lookup
    public A x() {
        System.out.println("哈哈哈哈，我是不会执行的");
        return a;
    }
}

@Component
class A {

}

@Component
class MyBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 测试 @Lookup 失效的情况
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(Test.class)
                .getBeanDefinition();
        beanDefinition.setInstanceSupplier(() -> {
            System.out.println("setInstanceSupplier--->");
            return new Test();
        });
        registry.registerBeanDefinition("SupplierTest", beanDefinition);
    }
}
```

### 原理
```java
/**
 * @Lookup 注解原理
 *
 * 1. 创建bean
 *      {@link AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])}
 *      {@link AbstractAutowireCapableBeanFactory#doCreateBean(String, RootBeanDefinition, Object[])}
 *      {@link AbstractAutowireCapableBeanFactory#createBeanInstance(String, RootBeanDefinition, Object[])}
 *
 * 2. 如果是 BeanDefinition设置了instanceSupplier属性，那就直接调用函数是接口返回实例对象。不会通过 {@link AbstractAutowireCapableBeanFactory#instantiateBean(String, RootBeanDefinition)} 的方式实例化，也就是@Lookup会失效
 *      {@link AbstractAutowireCapableBeanFactory#obtainFromSupplier(Supplier, String)}
 *
 * 3. 通过 AutowiredAnnotationBeanPostProcessor 处理 @Lookup 注解 给 bd 设置属性
 *      {@link AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors(Class, String)}
 *
 * 4. 实例化bean
 *      {@link AbstractAutowireCapableBeanFactory#instantiateBean(String, RootBeanDefinition)}
 *      {@link SimpleInstantiationStrategy#instantiate(RootBeanDefinition, String, BeanFactory)}
 *          1. bd.methodOverrides.isEmpty();：反射创建对象`BeanUtils.instantiateClass(constructorToUse);`
 *          2. 否则 cglib 创建代理对象 {@link CglibSubclassingInstantiationStrategy#instantiateWithMethodInjection(RootBeanDefinition, String, BeanFactory)}
 *                      Enhancer enhancer = new Enhancer();
 *                      setCallbackFilter(new MethodOverrideCallbackFilter(beanDefinition));
 *                      setCallbacks(new Callback[] {NoOp.INSTANCE,
 * 	        				new LookupOverrideMethodInterceptor(this.beanDefinition, this.owner),
 * 	        				new ReplaceOverrideMethodInterceptor(this.beanDefinition, this.owner)});
 *
 *          Tips：标注了@Lookup 注解的bean，在实例化的时候会返回cglib生成的代理对象，所以执行方法的时候就会被代理对象拦截，具体的拦截动作看 LookupOverrideMethodInterceptor
 *
 * 5. 增强逻辑是啥 {@link CglibSubclassingInstantiationStrategy.LookupOverrideMethodInterceptor#intercept(Object, Method, Object[], MethodProxy)}
 *      增强逻辑：@Lookup('') 有值，就通过参数值获取bean，没有就通过方法返回值类型获取bean `return getBean()`
 *
 * */
```
## @DependsOn

`@DependsOn` 表示依赖关系，在获取当前bean的时候会先获取`@DependsOn`的值。比如：在getBean(A) 的时候，会获取`@DependsOn` 的值，遍历注解的值 getBean(b)
```java
@Component
@DependsOn("b")
class A {

}

@Component
class B {

}
```

`@DependsOn` 原理
```java
/**
 * {@link AbstractBeanFactory#getBean(String, Class, Object...)}
 *
 * {@link AbstractBeanFactory#doGetBean(String, Class, Object[], boolean)}
 *
 *      单例池中存在bean，就返回不创建了 {@link DefaultSingletonBeanRegistry#getSingleton(String)}
 *
 *      当前BeanFactory中没有该bean的定义，且存在父BeanFactory 就调用父类的 {@link AbstractBeanFactory#doGetBean(String, Class, Object[], boolean)}
 *
 *      遍历当前bean的 @DependsOn 的值 执行 {@link AbstractBeanFactory#getBean(String)}
 *
 *      创建bean {@link AbstractBeanFactory#createBean(String, RootBeanDefinition, Object[])}
 * */
```
## @Lazy

```java
@Component
@Data
public class Test {
    @Autowired
    @Lazy
    private X x;

    @Autowired
    private X x2;
}

@Component
@Lazy
class X {

}
```

```java
/**
 * 创建bean
 * {@link AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])}
 * {@link AbstractAutowireCapableBeanFactory#doCreateBean(String, RootBeanDefinition, Object[])}
 *
 * 填充bean
 * {@link AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)}
 *
 * 后置处理器 解析属性值
 * {@link AutowiredAnnotationBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)}
 *      {@link InjectionMetadata#inject(Object, String, PropertyValues)}
 *      {@link AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#inject(Object, String, PropertyValues)}
 *      {@link AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#resolveFieldValue(Field, Object, String)}
 *      {@link DefaultListableBeanFactory#resolveDependency(DependencyDescriptor, String, Set, TypeConverter)}
 *          判断是否是 @Lazy 有就创建代理对象 {@link ContextAnnotationAutowireCandidateResolver#getLazyResolutionProxyIfNecessary(DependencyDescriptor, String)}
 *              {@link ContextAnnotationAutowireCandidateResolver#isLazy(DependencyDescriptor)}
 *              {@link ContextAnnotationAutowireCandidateResolver#buildLazyResolutionProxy(DependencyDescriptor, String)}
 *                  创建代理对象，两种策略 cglib 或者 jdk {@link ProxyFactory#getProxy(ClassLoader)}
 *                      cglib 的代理逻辑是这个 {@link CglibAopProxy.DynamicAdvisedInterceptor#intercept(Object, Method, Object[], MethodProxy)}
 *                      jdk 的代理逻辑是这个 {@link JdkDynamicAopProxy#invoke(Object, Method, Object[])}
 *                      
 * 将解析的属性值设置到bean中 {@link AbstractAutowireCapableBeanFactory#applyPropertyValues(String, BeanDefinition, BeanWrapper, PropertyValues)}
 * */
```
## @EventListener
源码解析：
```java
/**
 * 创建 IOC 容器 `new AnnotationConfigApplicationContext(AppConfig.class);`
 *
 * 构造器默认注入两个bean(后面有大用)
 *      - EventListenerMethodProcessor：
 *          - 作为 BeanFactoryPostProcessor 的功能。会存储IOC容器中所有的 EventListenerFactory 类型的bean，作为处理器的属性
 *          - 作为 SmartInitializingSingleton 的功能。用来处理 @EventListener 注解的，会在提前实例化单例bean的流程中 回调该实例的方法
 *
 *      - DefaultEventListenerFactory：用来创建 ApplicationListener
 *
 * 刷新 IOC 容器 {@link AbstractApplicationContext#refresh()}
 *
 * 完成 BeanFactory 的初始化 {@link AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)}
 *
 * 提前实例化单例bean {@link DefaultListableBeanFactory#preInstantiateSingletons()}
 *
 * 完成所有单例bean初始化后 {@link SmartInitializingSingleton#afterSingletonsInstantiated()}
 *      也就是一开始实例化IOC容器的时候 注入的这个 EventListenerMethodProcessor
 *
 * 回调 {@link EventListenerMethodProcessor#afterSingletonsInstantiated()}
 *      遍历容器里面所有的 bean 进行处理 {@link EventListenerMethodProcessor#processBean(String, Class)}
 *          使用 EventListenerFactory 判断是否适配 {@link EventListenerFactory#supportsMethod(Method)}
 *              将 @EventListener 解析成 ApplicationListener {@link DefaultEventListenerFactory#createApplicationListener(String, Class, Method)}
 *                  解析 @EventListener {@link ApplicationListenerMethodAdapter#resolveDeclaredEventTypes(Method, EventListener)}
 *              注册到 ApplicationListener 到 IOC 容器中 {@link ConfigurableApplicationContext#addApplicationListener(ApplicationListener)}
 * */
```
示例代码：
```java
@ComponentScan
public class Test extends AnnotationConfigApplicationContext {
    public Test() {
    }

    public Test(Class<?> clazz) {
        super(clazz);
    }

    @Override
    protected void onRefresh() throws BeansException {
        //  发布早期事件 测试一下
        publishEvent(new DemoEvent("早期事件"));
    }

    public static void main(String[] args) {
        Test test = new Test(Test.class);
        test.publishEvent(new DemoEvent("context刷新好了"));
    }
}

@Component
class MyEventListener {
    @EventListener(classes = DemoEvent.class)
    public void a(DemoEvent demoEvent) {
        /**
         * @EventListener 是在刷新bean的时候在解析注册的，所以 早期事件 是不能通过
         * */
        System.out.println("MyEventListener------>" + demoEvent);
    }
}

@Component
class MyApplicationListener implements ApplicationListener<DemoEvent> {
    @Override
    public void onApplicationEvent(DemoEvent event) {
        System.out.println("MyApplicationListener---->" + event);
    }
}

class DemoEvent extends ApplicationEvent {
    private static final long serialVersionUID = 7099057708183571937L;
    public DemoEvent(Object source) {
        super(source);
    }
}

```
## 注册事件监听器的两种方式

注册`ApplicationListener`的两种方式：

1. 在任意的一个bean 方法上标注 `@EventListener` 注解。方法只能有一个参数，该参数就是事件对象
2. 一个 bean 实现 `ApplicationListener` 接口

两种注册事件监听器的区别：

1. Spring 发布时间默认是通过 `ApplicationEventMulticaster` 进行广播的，该实例里面注册了IOC容器中类型 `ApplicationListener` 的 bean，当发布事件时 是遍历实例里所有的 `ApplicationListener` ,判断是否能适配，可以适配就回调`ApplicationListener#onApplicationEvent` 也就是要想`ApplicationListener` 能被回调，首先要注册到`ApplicationEventMulticaster` 中
2. 实现 `ApplicationListener` 接口的方式，是在实例化单例bean之前就注册到 `ApplicationEventMulticaster` 中
3. `@EventListener` 是在所有单例bean都注册到IOC容器后，才解析的。

注：所以如果在IOC容器创建单例bean的过程中发布事件，`@EventListener` 的方式是收不到的


```java
@ComponentScan
public class Test extends AnnotationConfigApplicationContext {

    public Test() {
    }

    public Test(Class<?> clazz) {
        super(clazz);
    }

    @Override
    protected void onRefresh() throws BeansException {
        //  发布早期事件 测试一下
        publishEvent(new DemoEvent("早期事件"));
    }

    public static void main(String[] args) {
        Test test = new Test(Test.class);
        test.publishEvent(new DemoEvent("context刷新好了"));
        /* 
控制台输出结果：
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=早期事件]
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=单例bean实例化事件]
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=单例bean初始化事件]
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=context刷新好了]
MyEventListener------>cn.haitaoss.javaconfig.EventListener.DemoEvent[source=context刷新好了]
         
        */
    }


}

@Component
class MyEventListener {
    @EventListener(classes = DemoEvent.class)
    public void a(DemoEvent demoEvent) {
        /**
         * @EventListener 是在刷新bean的时候在解析注册的，所以 早期事件 是不能通过
         * */
        System.out.println("MyEventListener------>" + demoEvent);
    }
}

@Component
class MyApplicationListener implements ApplicationListener<DemoEvent> {
    @Override
    public void onApplicationEvent(DemoEvent event) {
        System.out.println("MyApplicationListener---->" + event);
    }
}

class DemoEvent extends ApplicationEvent {
    private static final long serialVersionUID = 7099057708183571937L;

    public DemoEvent(Object source) {
        super(source);
    }
}


@Component
class SingleObject implements InitializingBean {
    @Autowired
    ApplicationEventMulticaster applicationEventMulticaster;

    public SingleObject(ApplicationEventMulticaster applicationEventMulticaster) {
        applicationEventMulticaster.multicastEvent(new DemoEvent("单例bean实例化事件"));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        applicationEventMulticaster.multicastEvent(new DemoEvent("单例bean初始化事件"));
    }
}
```
## ClassPathBeanDefinitionScanner

`ClassPathBeanDefinitionScanner` 是用来扫描包路径下的文件，判断是否符合bean的约定，满足就注册到BeanDefinitionMap中

两种扫描机制：

- 索引扫描：文件`META-INF/spring.components`内写上索引，只扫描索引里面的bean

  ```shell
  # key 是要注册的bean
  # value 是includefilter所能解析的注解,可以写多个默认按照`,`分割
  # 会在实例化 ClassPathBeanDefinitionScanner 的时候，解析 META-INF/spring.components 内容解析到 CandidateComponentsIndex 属性中 
  cn.haitaoss.service.UserService=org.springframework.stereotype.Component
  ```

- 包扫描：扫描包下面所有`.class`文件

注：包扫描，默认是扫描包下所有的`.class`文件，你可以搞花活，重写匹配文件的规则

### 原理分析

> 简单描述：
>
> 1. @ComponentScan 注解
> 2. 构造扫描器 ClassPathBeanDefinitionScanner
> 3. 根据 @ComponentScan 注解的属性配置扫描器
> 4. 扫描: 两种扫描方式
>    - 扫描指定的类：工具目录配置了 `resources/META-INF/spring.components` 内容，就只会扫描里面定义的类。这是Spring扫描的优化机制
>    - 扫描指定包下的所有类：获得扫描路径下所有的class文件（Resource对象）
> 5. 利用 ASM 技术读取class文件信息
> 6. ExcludeFile + IncludeFilter + @Conditional 的判断
> 7. 进行独立类、接口、抽象类 @Lookup的判断  `isCandidateComponent`
> 8. 判断生成的BeanDefinition是否重复
> 9. 添加到BeanDefinitionMap容器中

```java
/**
 * 构造器 {@link ClassPathBeanDefinitionScanner#ClassPathBeanDefinitionScanner(BeanDefinitionRegistry)}
 *      构造器会设置这些属性：
 *      1. this.registry = registry; 因为需要将解析的结果注册到IOC容器中，所以必须要得给个IOC容器
 *      2. 如果参数 useDefaultFilters == true，那么就设置添加默认的(识别@Component注解的) includeFilter {@link ClassPathScanningCandidateComponentProvider#registerDefaultFilters()}
 *          useDefaultFilters 默认就是true
 *      3. setEnvironment(environment); 就是用来读取系统属性和环境变量的
 *      4. setResourceLoader(resourceLoader); 这个很关键，扫描优化机制  {@link ClassPathScanningCandidateComponentProvider#setResourceLoader(ResourceLoader)}
 *          会设置这个属性 componentsIndex，该属性的实例化是执行 {@link CandidateComponentsIndexLoader#loadIndex(ClassLoader)}
 *              然后执行 {@link CandidateComponentsIndexLoader#doLoadIndex(ClassLoader)}
 *              就是会读取ClassLoader里面所有的 META-INF/spring.components 文件 {@link CandidateComponentsIndexLoader#COMPONENTS_RESOURCE_LOCATION}
 *              解析的结果存到 CandidateComponentsIndex 的 LinkedMultiValueMap<String, Entry> 属性中。数据格式： key:注解的全类名 Entry<bean全类名,包名>
 *
 *                 举例：META-INF/spring.components
 *                 cn.haitaoss.service.UserService=org.springframework.stereotype.Component
 *                 解析的结果就是 < org.springframework.stereotype.Component , Entry(cn.haitaoss.service.UserService,cn.haitaoss.service) >
 *
 *
 * 执行扫描 {@link ClassPathBeanDefinitionScanner#doScan(String...)}
 *      1. 入参就是包名，遍历包路径，查找候选的组件 {@link ClassPathScanningCandidateComponentProvider#findCandidateComponents(String)}
 *          有两种查找机制(会将查找结果返回)：
 *              第一种：属性componentsIndex不为空(也就是存在META-INF/spring.components) 且 所有includeFilter都满足{@link ClassPathScanningCandidateComponentProvider#indexSupportsIncludeFilter(TypeFilter)}
 *                     走索引优化策略 {@link ClassPathScanningCandidateComponentProvider#addCandidateComponentsFromIndex(CandidateComponentsIndex, String)}
 *
 *              第二种：扫描包下所有的资源 {@link ClassPathScanningCandidateComponentProvider#scanCandidateComponents(String)}
 *
 *      2. 返回结果，检查容器中是否存在这个 BeanDefinition，{@link ClassPathBeanDefinitionScanner#checkCandidate(String, BeanDefinition)}
 *
 *      3. 返回结果 注册到 BeanDefinitionMap 中 {@link ClassPathBeanDefinitionScanner#registerBeanDefinition(BeanDefinitionHolder, BeanDefinitionRegistry)}
 *
 *
 * 第一种查找机制流程：{@link ClassPathScanningCandidateComponentProvider#addCandidateComponentsFromIndex(CandidateComponentsIndex, String)}
 *      - 遍历includeFilters属性，拿到要扫描的注解值(默认就是@Compoent)，这个就是key {@link ClassPathScanningCandidateComponentProvider#extractStereotype(TypeFilter)}
 *      - key 取 CandidateComponentsIndex#index，拿到的就是 META-INF/spring.components 按照value分组后的key的集合信息
 *             然后判断 META-INF/spring.components 文件内容定义的bean的包名是否满足 扫描的包路径 {@link CandidateComponentsIndex#getCandidateTypes(String, String)}
 *      - 进行 ExcludeFiles + IncludeFilters + @Conditional 判断 {@link ClassPathScanningCandidateComponentProvider#isCandidateComponent(MetadataReader)}
 *      - 进行独立类、接口、抽象类 @Lookup的判断 {@link ClassPathScanningCandidateComponentProvider#isCandidateComponent(AnnotatedBeanDefinition)}
 *      - 满足条件添加到集合 candidates 中
 *
 * 第二种查找机制：{@link ClassPathScanningCandidateComponentProvider#scanCandidateComponents(String)}
 *      - 拿到包下所有的 class 文件 {@link ClassPathScanningCandidateComponentProvider#DEFAULT_RESOURCE_PATTERN}
 *      - 进行 ExcludeFiles + IncludeFilters + @Conditional 判断 {@link ClassPathScanningCandidateComponentProvider#isCandidateComponent(MetadataReader)}
 *      - 进行独立类、接口、抽象类 @Lookup的判断 {@link ClassPathScanningCandidateComponentProvider#isCandidateComponent(AnnotatedBeanDefinition)}
 *      - 满足条件添加到集合 candidates 中
 * */
```

### 索引扫描判断流程

```java
/**
 * 索引扫描判断流程：
 *
 * 1. 扫描组件 {@link ClassPathScanningCandidateComponentProvider#findCandidateComponents(String)}
 *
 * 2. 判断扫描器的 includeFilters 是否都支持索引扫描 {@link org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#indexSupportsIncludeFilters()}
 *
 * 3. 判断是否支持索引扫描的逻辑{@link ClassPathScanningCandidateComponentProvider#indexSupportsIncludeFilter(TypeFilter)}
 *      是这种类型 filter instanceof AnnotationTypeFilter
 *          filter.getAnnotationType() 有@Indexed注解 或者 是javax. 包下的类
 *
 *      是这种类型 filter instanceof AssignableTypeFilter
 *          filter.getTargetType() 有@Indexed注解
 */
```

### AnnotationTypeFilter 匹配逻辑

```java
/**
 * {@link AbstractTypeHierarchyTraversingFilter#match(MetadataReader, MetadataReaderFactory)}
 * 
 * 1. 匹配bean是否有注解 {@link AbstractTypeHierarchyTraversingFilter#matchSelf(MetadataReader)}
 *      返回true，就return
 *
 * 2. 属性：considerInherited 为 true(通过构造器设置的)
 *      bean的父类 {@link AbstractTypeHierarchyTraversingFilter#matchSuperClass(String)}
 *          返回true，就return
 *      递归调 {@link AbstractTypeHierarchyTraversingFilter#match(MetadataReader, MetadataReaderFactory)}
 *
 * 3. 属性：considerInterfaces 为 true(通过构造器设置的)
 *      bean的接口 {@link AbstractTypeHierarchyTraversingFilter#matchInterface(String)}
 *          返回true，就return
 *      递归调 {@link AbstractTypeHierarchyTraversingFilter#match(MetadataReader, MetadataReaderFactory)}
 * */
```

### @ComponentScan

```java
@ComponentScan(
        basePackages = "cn", // 扫描包路径
        useDefaultFilters = true, // 是否注册默认的 includeFilter，默认会注解扫描@Component注解的includeFilter
        nameGenerator = BeanNameGenerator.class, // beanName 生成器
    		excludeFilters = {} // 扫描bean 排除filter。其中一个命中就不能作为bean
        includeFilters = {@ComponentScan.Filter(type = FilterType.CUSTOM, classes = MyAnnotationTypeFilter.class)} // 扫描bean 包含filter。其中一个命中就能作为bean
)
public class A{}
```

### 索引扫描示例

`META-INF/spring.components` 文件

```properties
cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.AService=cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.MyAnnotationTypeFilter$MyAnnotation

cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.AService=cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.MyAnnotationTypeFilter$Haitao
```

代码：

```java
@Component
/*@ComponentScan( 
        basePackages = "cn",
        useDefaultFilters = true,
        nameGenerator = BeanNameGenerator.class,
        includeFilters = {@ComponentScan.Filter(type = FilterType.CUSTOM, classes = MyAnnotationTypeFilter.class)}, // 这个可以重写 AbstractTypeHierarchyTraversingFilter#match 定制匹配规则
        excludeFilters = {}
)*/
@ComponentScan(includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, classes = MyAnnotationTypeFilter.Haitao.class)}) // 这个用起来方便，有这个注解 就可以
public class Test {}

@MyAnnotationTypeFilter.Haitao
class AService {}

/**
 * 索引扫描判断流程：
 *
 * 1. 扫描组件 {@link org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#findCandidateComponents(String)}
 *
 * 2. 判断扫描器的 includeFilters 是否都支持索引扫描 {@link org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#indexSupportsIncludeFilters()}
 *
 * 3. 判断是否支持索引扫描的逻辑{@link org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#indexSupportsIncludeFilter(TypeFilter)}
 *      是这种类型 filter instanceof AnnotationTypeFilter
 *          filter.getAnnotationType() 有@Indexed注解 或者 是javax. 包下的类
 *
 *      是这种类型 filter instanceof AssignableTypeFilter
 *          filter.getTargetType() 有@Indexed注解
 */

/**
 * 对应的配置文件：META-INF/spring.components
 * - cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.AService=cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.MyAnnotationTypeFilter$MyAnnotation
 * - cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.AService=cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.MyAnnotationTypeFilter$Haitao
 * */
class MyAnnotationTypeFilter extends AnnotationTypeFilter {
    @Indexed // 这个是必须的，否则无法使用 索引扫描
    class MyAnnotation implements Annotation {
        @Override
        public Class<? extends Annotation> annotationType() {
            return MyAnnotation.class;
        }
    }

    @Target(ElementType.TYPE)
    @Indexed
    @Retention(RetentionPolicy.RUNTIME)
    @interface Haitao {}

    public MyAnnotationTypeFilter() {
        // super(MyAnnotation.class);
        super(Haitao.class);
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        // 匹配方法
        return true;
    }
}

```
## @Bean 如何解析的

```java
/**
 * 1. IOC 容器初始化
 * @see org.springframework.context.support.AbstractApplicationContext#refresh()
 * 2. 执行 BeanFactory 的后置处理器
 * @see org.springframework.context.support.AbstractApplicationContext#invokeBeanFactoryPostProcessors(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
 * 3. invokeBeanDefinitionRegistryPostProcessors 动态注册BeanDefinition。
 * @see org.springframework.context.support.PostProcessorRegistrationDelegate#invokeBeanDefinitionRegistryPostProcessors(java.util.Collection, org.springframework.beans.factory.support.BeanDefinitionRegistry, org.springframework.core.metrics.ApplicationStartup)
 * 4. javaConfig 就是通过 ConfigurationClassPostProcessor 来注册beanDefinition的
 * @see org.springframework.context.annotation.ConfigurationClassPostProcessor#processConfigBeanDefinitions(org.springframework.beans.factory.support.BeanDefinitionRegistry)
 * 5. 解析。解析配置类里面的：配置类、@Bean、@ImportResource、@Import
 * @see org.springframework.context.annotation.ConfigurationClassParser#parse(java.util.Set)
 *  5.1 将@Bean标注的方法添加到configClass中。针对父类里面有@Bean的方法，会把已经处理过的父类 存到 knownSuperclasses 这个Map中，避免重复处理
 *      @see org.springframework.context.annotation.ConfigurationClassParser#doProcessConfigurationClass(org.springframework.context.annotation.ConfigurationClass, org.springframework.context.annotation.ConfigurationClassParser.SourceClass, java.util.function.Predicate)
 *      @see configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
 * 6. 从解析完的 configClass 中加载BeanDefinition
 * @see org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForConfigurationClass(org.springframework.context.annotation.ConfigurationClass, org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator)
 * 7. 将 BeanMethod 解析完，然后添加到beanDefinitionMap中
 * @see org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForBeanMethod(org.springframework.context.annotation.BeanMethod)
 * */
```

## @Conditional

> ClassPathBeanDefinitionScanner 在扫描bean 注册到BeanFactory的时候会进行判断：ExcludeFile -> IncludeFilter -> @Conditional 的判断
>
> ```java
> /**
>  * 注意：
>  * {@link ClassPathScanningCandidateComponentProvider#isConditionMatch(MetadataReader)}
>  * {@link ClassPathScanningCandidateComponentProvider#isConditionMatch(MetadataReader)}
>  * {@link ConditionEvaluator#shouldSkip(AnnotatedTypeMetadata, *  ConfigurationCondition.ConfigurationPhase)}
>  * {@link Condition#matches(ConditionContext, AnnotatedTypeMetadata)}
>  *      第一个参数是当前的BeanFactory，此时的BeanFactory并不完整，要想保证 @Conditional 能正确判断，应当保证 bean 注册到 BeanFactory 的先后顺序
>  */
> ```
>
> 扩展知识：SpringBoot的自动转配用到了很多 @Conditional。而SpringBoot是通过@Import(DeferredImportSelector.class) 延时bean注册到BeanFactory中，从而尽可能的保证 @Conditional 判断的准确性

```java
@FunctionalInterface
public interface Condition {
    /**
     *
     * @param context 这里面可以拿到，beanFactory 已经注册的beanDefinition 和 单例bean。所以Condition的判断只能是判断当前环境的
     * @param metadata
     * @return
     */
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

```java
@Component
public class Test {
    static class A {}

    static class MyCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return true;
        }
    }

    @Bean
    @Conditional(MyCondition.class)
    public A a() {
        return new A();
    }
}

```



# 待整理



## Spring 整合 Mybatis

Mybatis 官网：https://mybatis.org/mybatis-3/getting-started.html



## 类型转换

### bean的生命周期中时候会使用 TypeConverter?

```java
/**
 * 创建bean -> 填充bean -> AutowiredAnnotationBeanPostProcessor#postProcessProperties -> resolveFieldValue -> getTypeConverter
 * @see AbstractAutowireCapableBeanFactory#doCreateBean(String, RootBeanDefinition, Object[])
 * @see AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)
 * @see AutowiredAnnotationBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)
 * @see AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#resolveFieldValue(Field, Object, String)
 * @see AbstractBeanFactory#getTypeConverter()
 * 注：类型转换的功能是通过 ConversionService 实现的
 * */
```

### 什么时候往 TypeConverter 中设置 conversionService? IOC 容器refresh环节

```java
/**
 * 会在这里从容器中获取一个name 是 conversionService 的bean 进行注入
 * @see org.springframework.context.support.AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)
 */
```

### 有哪些 ConversionService ？

其中 DefaultFormattingConversionService 功能强大： 类型转换 + 格式化
![img.png](.README_imgs/img2.png)

```java

@Component
public class TestConversionService {
    @Value("2022-08-11")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date = new Date();

    @Value("101.11")
    @NumberFormat(pattern = "#")
    private Integer money;

    @Value("code,play")
    private String[] jobs;

    @Override
    public String toString() {
        return "TestConversionService{" + "date=" + date + ", money=" + money + ", jobs=" + Arrays.toString(jobs) + '}';
    }
}
```

### 如何自定义类型装换？

```java

@Component
public class TestConversionService {
    @Value("haitaoss")
    private Person person;
}
```

```java
public class AppConfig {
    public AppConfig() {
        System.out.println("AppConfig 构造器");
    }

    @Bean
    public FormattingConversionService conversionService() {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new String2PersonConverter());
        return conversionService;
    }
}

class String2PersonConverter implements ConditionalGenericConverter {
    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return String.class.isAssignableFrom(sourceType.getType())
               && Person.class.isAssignableFrom(targetType.getType());
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(String.class, Person.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        Person person = new Person();
        person.setName(source + "--->");
        return person;
    }
}
```

