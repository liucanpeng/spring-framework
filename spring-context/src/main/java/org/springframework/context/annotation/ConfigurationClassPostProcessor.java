/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.NativeDetector;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.*;
import java.util.function.Predicate;

/**
 * {@link BeanFactoryPostProcessor} used for bootstrapping processing of
 * {@link Configuration @Configuration} classes.
 *
 * <p>Registered by default when using {@code <context:annotation-config/>} or
 * {@code <context:component-scan/>}. Otherwise, may be declared manually as
 * with any other {@link BeanFactoryPostProcessor}.
 *
 * <p>This post processor is priority-ordered as it is important that any
 * {@link Bean @Bean} methods declared in {@code @Configuration} classes have
 * their corresponding bean definitions registered before any other
 * {@code BeanFactoryPostProcessor} executes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered, ResourceLoaderAware, ApplicationStartupAware, BeanClassLoaderAware, EnvironmentAware {

    /**
     * A {@code BeanNameGenerator} using fully qualified class names as default bean names.
     * <p>This default for configuration-level import purposes may be overridden through
     * {@link #setBeanNameGenerator}. Note that the default for component scanning purposes
     * is a plain {@link AnnotationBeanNameGenerator#INSTANCE}, unless overridden through
     * {@link #setBeanNameGenerator} with a unified user-level bean name generator.
     * @since 5.2
     * @see #setBeanNameGenerator
     */
    public static final AnnotationBeanNameGenerator IMPORT_BEAN_NAME_GENERATOR = FullyQualifiedAnnotationBeanNameGenerator.INSTANCE;

    private static final String IMPORT_REGISTRY_BEAN_NAME =
            ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


    private final Log logger = LogFactory.getLog(getClass());

    private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

    private ProblemReporter problemReporter = new FailFastProblemReporter();

    @Nullable
    private Environment environment;

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Nullable
    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

    private boolean setMetadataReaderFactoryCalled = false;

    private final Set<Integer> registriesPostProcessed = new HashSet<>();

    private final Set<Integer> factoriesPostProcessed = new HashSet<>();

    @Nullable
    private ConfigurationClassBeanDefinitionReader reader;

    private boolean localBeanNameGeneratorSet = false;

    /* Using short class names as default bean names by default. */
    private BeanNameGenerator componentScanBeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

    /* Using fully qualified class names as default bean names by default. */
    private BeanNameGenerator importBeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR;

    private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;


    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
    }

    /**
     * Set the {@link SourceExtractor} to use for generated bean definitions
     * that correspond to {@link Bean} factory methods.
     */
    public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
        this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
    }

    /**
     * Set the {@link ProblemReporter} to use.
     * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
     * declarations. For instance, an @Bean method marked as {@code final} is illegal
     * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
     */
    public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
        this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
    }

    /**
     * Set the {@link MetadataReaderFactory} to use.
     * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
     * {@linkplain #setBeanClassLoader bean class loader}.
     */
    public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
        Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
        this.metadataReaderFactory = metadataReaderFactory;
        this.setMetadataReaderFactoryCalled = true;
    }

    /**
     * Set the {@link BeanNameGenerator} to be used when triggering component scanning
     * from {@link Configuration} classes and when registering {@link Import}'ed
     * configuration classes. The default is a standard {@link AnnotationBeanNameGenerator}
     * for scanned components (compatible with the default in {@link ClassPathBeanDefinitionScanner})
     * and a variant thereof for imported configuration classes (using unique fully-qualified
     * class names instead of standard component overriding).
     * <p>Note that this strategy does <em>not</em> apply to {@link Bean} methods.
     * <p>This setter is typically only appropriate when configuring the post-processor as a
     * standalone bean definition in XML, e.g. not using the dedicated {@code AnnotationConfig*}
     * application contexts or the {@code <context:annotation-config>} element. Any bean name
     * generator specified against the application context will take precedence over any set here.
     * @since 3.1.1
     * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
     * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
     */
    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
        this.localBeanNameGeneratorSet = true;
        this.componentScanBeanNameGenerator = beanNameGenerator;
        this.importBeanNameGenerator = beanNameGenerator;
    }

    @Override
    public void setEnvironment(Environment environment) {
        Assert.notNull(environment, "Environment must not be null");
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
        if (!this.setMetadataReaderFactoryCalled) {
            this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
        }
    }

    @Override
    public void setBeanClassLoader(ClassLoader beanClassLoader) {
        this.beanClassLoader = beanClassLoader;
        if (!this.setMetadataReaderFactoryCalled) {
            this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
        }
    }

    @Override
    public void setApplicationStartup(ApplicationStartup applicationStartup) {
        this.applicationStartup = applicationStartup;
    }

    /**
     * Derive further bean definitions from the configuration classes in the registry.
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        int registryId = System.identityHashCode(registry);
        if (this.registriesPostProcessed.contains(registryId)) {
            throw new IllegalStateException(
                    "postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
        }
        if (this.factoriesPostProcessed.contains(registryId)) {
            throw new IllegalStateException(
                    "postProcessBeanFactory already called on this post-processor against " + registry);
        }
        this.registriesPostProcessed.add(registryId);

        // 解析配置类
        processConfigBeanDefinitions(registry);
    }

    /**
     * Prepare the Configuration classes for servicing bean requests at runtime
     * by replacing them with CGLIB-enhanced subclasses.
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        int factoryId = System.identityHashCode(beanFactory);
        if (this.factoriesPostProcessed.contains(factoryId)) {
            throw new IllegalStateException(
                    "postProcessBeanFactory already called on this post-processor against " + beanFactory);
        }
        this.factoriesPostProcessed.add(factoryId);
        if (!this.registriesPostProcessed.contains(factoryId)) {
            // BeanDefinitionRegistryPostProcessor hook apparently not supported...
            // Simply call processConfigurationClasses lazily at this point then.
            processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
        }

        /**
         * 增强配置类class
         * */
        enhanceConfigurationClasses(beanFactory);
        /**
         * 添加一个后置处理器，用来处理 ImportAware接口、给full配置类设置属性
         * */
        beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
    }

    /**
     * Build and validate a configuration model based on the registry of
     * {@link Configuration} classes.
     */
    public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
        List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
        /**
         * 1. 启动时传入的配置类会注册到容器中 {@link AnnotationConfigApplicationContext#register(Class[])}
         * 2. BeanFactoryPostProcessor、BeanDefinitionRegistryPostProcessor 里面注册 {@link AbstractApplicationContext#invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory)}
         * */
        String[] candidateNames = registry.getBeanDefinitionNames();

        for (String beanName : candidateNames) {
            BeanDefinition beanDef = registry.getBeanDefinition(beanName);
            if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
                }
            }
            /**
             * 判断是否是配置类
             * 有 @Configuration(proxyBeanMethods=true) full配置类
             * 有 @Configuration(proxyBeanMethods=false) lite配置类
             * 无 @Configuration 但是有  (@Component || @ComponentScan || @Import || @ImportResource || @Bean ) lite配置类
             *
             * 是配置类，还会解析 @Order 注解的值，设置到 BeanDefinition 中。目的是后面配置类排序
             * */
            else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
                configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
            }
        }

        // Return immediately if no @Configuration classes were found
        if (configCandidates.isEmpty()) {
            return;
        }

        /**
         * 通过 Order可以排序，升序排序， order越小越靠前。
         * 可以在配置类上标注 @Order，调整配置类的解析顺序。
         * 升序排列。
         * */
        // Sort by previously determined @Order value, if applicable
        configCandidates.sort((bd1, bd2) -> {
            int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
            int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
            return Integer.compare(i1, i2);
        });

        // Detect any custom bean name generation strategy supplied through the enclosing application context
        SingletonBeanRegistry sbr = null;
        if (registry instanceof SingletonBeanRegistry) {
            sbr = (SingletonBeanRegistry) registry;
            if (!this.localBeanNameGeneratorSet) {
                // 可以预先往单例池中添加一个 CONFIGURATION_BEAN_NAME_ GENERATOR的 BeanNameGenerator类型的bean
                // 可以用来作为扫描得到的Bean和 import导入进来的Bean的 beanName
                BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
                if (generator != null) {
                    this.componentScanBeanNameGenerator = generator;
                    this.importBeanNameGenerator = generator;
                }
            }
        }

        if (this.environment == null) {
            this.environment = new StandardEnvironment();
        }

        // Parse each @Configuration class
        ConfigurationClassParser parser = new ConfigurationClassParser(this.metadataReaderFactory, this.problemReporter, this.environment, this.resourceLoader, this.componentScanBeanNameGenerator, registry);

        Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
        Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
        do {
            StartupStep processConfig = this.applicationStartup.start("spring.context.config-classes.parse");

            /**
             * 解析配置类，会把每个 BeanDefinitionHolder 解析为 ConfigurationClass
             * 会解析 @Bean、@Component、@ComponentScan、@ComponentScans、@Import、@ImportResource 也就是会解析出配置类里面的配置类。递归解析
             *
             * 解析完 configClasses，结果是：
             * 	1. @Bean 标注的方法，  --> beanMethods属性
             * 	2. @ImportResource  --> importedResources属性
             * 	3. @Import(ImportBeanDefinitionRegistrar.class)  --> importBeanDefinitionRegistrars属性，后面会回调方法
             *  4. @Import(ImportSelector.class) --> 执行 ImportSelector#selectImports，返回值都解析成配置类
             *  5. @Import() --> 解析成configClass
             *  6. @Import(DeferredImportSelector.class) --> deferredImportSelectorHandler
             *
             * 	注：
             * 	1. 使用 @Import 导入的类一定是配置类
             * 	2. @Import(DeferredImportSelector.class) 会延时解析，SpringBoot 的自动转配 就是通过这个机制实现的。通过延时解析保证 @ConditionalOnXx 注解能正确判断
             */
            parser.parse(candidates); // AppConfig.class ---> BeanDefinition
            parser.validate();

            // configClasses 相当于就是解析之后的结果
            Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
            configClasses.removeAll(alreadyParsed);

            // Read the model and create bean definitions based on its content
            if (this.reader == null) {
                this.reader = new ConfigurationClassBeanDefinitionReader(registry, this.sourceExtractor, this.resourceLoader, this.environment, this.importBeanNameGenerator, parser.getImportRegistry());
            }
            /**
             * TODOHAITAO 【@Bean 第三步】因为解析完的 configClasses 里面会记录 @Bean标注的方法，这个方法被称为 factoryMethod
             * 	会在下面的方法注册成 beanDefinition
             */
            this.reader.loadBeanDefinitions(configClasses);
            alreadyParsed.addAll(configClasses);
            processConfig.tag("classCount", () -> String.valueOf(configClasses.size()))
                    .end();

            candidates.clear();
            if (registry.getBeanDefinitionCount() > candidateNames.length) {
                String[] newCandidateNames = registry.getBeanDefinitionNames();
                Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
                Set<String> alreadyParsedClasses = new HashSet<>();
                for (ConfigurationClass configurationClass : alreadyParsed) {
                    alreadyParsedClasses.add(configurationClass.getMetadata()
                            .getClassName());
                }
                for (String candidateName : newCandidateNames) {
                    if (!oldCandidateNames.contains(candidateName)) {
                        BeanDefinition bd = registry.getBeanDefinition(candidateName);
                        if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory)
                                && !alreadyParsedClasses.contains(bd.getBeanClassName())) {
                            candidates.add(new BeanDefinitionHolder(bd, candidateName));
                        }
                    }
                }
                candidateNames = newCandidateNames;
            }
        } while (!candidates.isEmpty());

        /**
         * 注册 记录了@Import(A.class）的信息到BeanFactory中，在这里会用到
         * {@link ImportAwareBeanPostProcessor#postProcessBeforeInitialization(Object, String)}
         * */
        // Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
        if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
            sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
        }

        if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
            // Clear cache in externally provided MetadataReaderFactory; this is a no-op
            // for a shared cache since it'll be cleared by the ApplicationContext.
            ((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
        }
    }

    /**
     * Post-processes a BeanFactory in search of Configuration class BeanDefinitions;
     * any candidates are then enhanced by a {@link ConfigurationClassEnhancer}.
     * Candidate status is determined by BeanDefinition attribute metadata.
     * @see ConfigurationClassEnhancer
     */
    public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
        StartupStep enhanceConfigClasses = this.applicationStartup.start("spring.context.config-classes.enhance");
        // 记录full配置类
        Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
            Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
            AnnotationMetadata annotationMetadata = null;
            MethodMetadata methodMetadata = null;
            if (beanDef instanceof AnnotatedBeanDefinition) {
                AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDef;
                annotationMetadata = annotatedBeanDefinition.getMetadata();
                methodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
            }
            /**
             * 在这里标记是full配置类还是lite配置类 {@link ConfigurationClassUtils#checkConfigurationClassCandidate(BeanDefinition, MetadataReaderFactory)}
             * */
            if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition) {
                // Configuration class (full or lite) or a configuration-derived @Bean method
                // -> eagerly resolve bean class at this point, unless it's a 'lite' configuration
                // or component class without @Bean methods.
                AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDef;
                if (!abd.hasBeanClass()) {
                    // 是 lite配置类 但是没有 @Bean方法
                    boolean liteConfigurationCandidateWithoutBeanMethods = (
                            ConfigurationClassUtils.CONFIGURATION_CLASS_LITE.equals(configClassAttr)
                                    && annotationMetadata != null
                                    && !ConfigurationClassUtils.hasBeanMethods(annotationMetadata));
                    if (!liteConfigurationCandidateWithoutBeanMethods) {
                        try {
                            // 记录
                            abd.resolveBeanClass(this.beanClassLoader);
                        } catch (Throwable ex) {
                            throw new IllegalStateException(
                                    "Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
                        }
                    }
                }
            }
            // 是 full 配置类
            if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
                if (!(beanDef instanceof AbstractBeanDefinition)) {
                    throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" + beanName
                            + "' since it is not stored in an AbstractBeanDefinition subclass");
                } else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
                    logger.info("Cannot enhance @Configuration bean definition '" + beanName
                            + "' since its singleton instance has been created too early. The typical cause "
                            + "is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor "
                            + "return type: Consider declaring such methods as 'static'.");
                }
                // 记录
                configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
            }
        }
        if (configBeanDefs.isEmpty() || NativeDetector.inNativeImage()) {
            // nothing to enhance -> return immediately
            enhanceConfigClasses.end();
            return;
        }

        // 这里就是遍历所有的 full配置类，使用cglib生成代理类的class
        ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
        for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
            AbstractBeanDefinition beanDef = entry.getValue();
            // If a @Configuration class gets proxied, always proxy the target class
            beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
            // Set enhanced subclass of the user-specified bean class
            Class<?> configClass = beanDef.getBeanClass();
            /**
             * 这里就是使用cglib生成代理类
             * */
            Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
            if (configClass != enhancedClass) {
                if (logger.isTraceEnabled()) {
                    logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with "
                            + "enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
                }
                // 将cglib生成的class 设置到BeanDefinition中
                beanDef.setBeanClass(enhancedClass);
            }
        }
        enhanceConfigClasses.tag("classCount", () -> String.valueOf(configBeanDefs.keySet()
                        .size()))
                .end();
    }


    private static class ImportAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

        private final BeanFactory beanFactory;

        public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
            // Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
            // postProcessProperties method attempts to autowire other configuration beans.
            if (bean instanceof EnhancedConfiguration) {
                ((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
            }
            return pvs;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof ImportAware) {
                /**
                 * 是在配置类的processor注册的
                 * {@link ConfigurationClassPostProcessor#postProcessBeanFactory(ConfigurableListableBeanFactory)}
                 * */
                ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
                /**
                 * 这个信息是扫描配置类的时候记录的
                 * {@link ConfigurationClassParser#processImports(ConfigurationClass, ConfigurationClassParser.SourceClass, Collection, Predicate, boolean)}
                 * */
                AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean)
                        .getName());
                if (importingClass != null) {
                    ((ImportAware) bean).setImportMetadata(importingClass);
                }
            }
            return bean;
        }
    }

}
