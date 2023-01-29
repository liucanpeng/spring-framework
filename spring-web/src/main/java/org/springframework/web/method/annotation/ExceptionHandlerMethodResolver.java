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

package org.springframework.web.method.annotation;

import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Discovers {@linkplain ExceptionHandler @ExceptionHandler} methods in a given class,
 * including all of its superclasses, and helps to resolve a given {@link Exception}
 * to the exception types supported by a given {@link Method}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 */
public class ExceptionHandlerMethodResolver {

    /**
     * A filter for selecting {@code @ExceptionHandler} methods.
     */
    public static final MethodFilter EXCEPTION_HANDLER_METHODS = method -> AnnotatedElementUtils.hasAnnotation(
            method, ExceptionHandler.class);

    private static final Method NO_MATCHING_EXCEPTION_HANDLER_METHOD;

    static {
        try {
            NO_MATCHING_EXCEPTION_HANDLER_METHOD = ExceptionHandlerMethodResolver.class.getDeclaredMethod(
                    "noMatchingExceptionHandler");
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("Expected method not found: " + ex);
        }
    }


    private final Map<Class<? extends Throwable>, Method> mappedMethods = new HashMap<>(16);

    private final Map<Class<? extends Throwable>, Method> exceptionLookupCache = new ConcurrentReferenceHashMap<>(16);


    /**
     * A constructor that finds {@link ExceptionHandler} methods in the given type.
     *
     * @param handlerType the type to introspect
     */
    public ExceptionHandlerMethodResolver(Class<?> handlerType) {
        // 遍历有@ExceptionHandler 注解的方法
        for (Method method : MethodIntrospector.selectMethods(handlerType, EXCEPTION_HANDLER_METHODS)) {
            // 拿到@ExceptionHandler(Exception.class)，没有注解值，就通过方法参数获取 Throwable 类型
            for (Class<? extends Throwable> exceptionType : detectExceptionMappings(method)) {
                // 注册映射关系
                addExceptionMapping(exceptionType, method);
            }
        }
    }


    /**
     * Extract exception mappings from the {@code @ExceptionHandler} annotation first,
     * and then as a fallback from the method signature itself.
     */
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> detectExceptionMappings(Method method) {
        List<Class<? extends Throwable>> result = new ArrayList<>();
        // 拿到@ExceptionHandler(Exception.class) 记录在 result 中
        detectAnnotationExceptionMappings(method, result);
        // 兜底操作
        if (result.isEmpty()) {
            // 遍历方法参数
            for (Class<?> paramType : method.getParameterTypes()) {
                if (Throwable.class.isAssignableFrom(paramType)) {
                    // 记录起来
                    result.add((Class<? extends Throwable>) paramType);
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No exception types mapped to " + method);
        }
        return result;
    }

    private void detectAnnotationExceptionMappings(Method method, List<Class<? extends Throwable>> result) {
        ExceptionHandler ann = AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
        Assert.state(ann != null, "No ExceptionHandler annotation");
        result.addAll(Arrays.asList(ann.value()));
    }

    private void addExceptionMapping(Class<? extends Throwable> exceptionType, Method method) {
        Method oldMethod = this.mappedMethods.put(exceptionType, method);
        /**
         * 不允许一个方法有 重复的异常类型
         * 不允许一个类中 多个方法对应一个异常类型
         * */
        if (oldMethod != null && !oldMethod.equals(method)) {
            throw new IllegalStateException(
                    "Ambiguous @ExceptionHandler method mapped for [" + exceptionType + "]: {" + oldMethod + ", " + method + "}");
        }
    }

    /**
     * Whether the contained type has any exception mappings.
     */
    public boolean hasExceptionMappings() {
        return !this.mappedMethods.isEmpty();
    }

    /**
     * Find a {@link Method} to handle the given exception.
     * <p>Uses {@link ExceptionDepthComparator} if more than one match is found.
     *
     * @param exception the exception
     * @return a Method to handle the exception, or {@code null} if none found
     */
    @Nullable
    public Method resolveMethod(Exception exception) {
        return resolveMethodByThrowable(exception);
    }

    /**
     * Find a {@link Method} to handle the given Throwable.
     * <p>Uses {@link ExceptionDepthComparator} if more than one match is found.
     *
     * @param exception the exception
     * @return a Method to handle the exception, or {@code null} if none found
     * @since 5.0
     */
    @Nullable
    public Method resolveMethodByThrowable(Throwable exception) {
        // 解析，看是否有处理这个异常的方法
        Method method = resolveMethodByExceptionType(exception.getClass());
        if (method == null) {
            Throwable cause = exception.getCause();
            if (cause != null) {
                // 递归处理
                method = resolveMethodByThrowable(cause);
            }
        }
        return method;
    }

    /**
     * Find a {@link Method} to handle the given exception type. This can be
     * useful if an {@link Exception} instance is not available (e.g. for tools).
     * <p>Uses {@link ExceptionDepthComparator} if more than one match is found.
     *
     * @param exceptionType the exception type
     * @return a Method to handle the exception, or {@code null} if none found
     */
    @Nullable
    public Method resolveMethodByExceptionType(Class<? extends Throwable> exceptionType) {
        Method method = this.exceptionLookupCache.get(exceptionType);
        if (method == null) {
            method = getMappedMethod(exceptionType);
            this.exceptionLookupCache.put(exceptionType, method);
        }
        return (method != NO_MATCHING_EXCEPTION_HANDLER_METHOD ? method : null);
    }

    /**
     * Return the {@link Method} mapped to the given exception type, or
     * {@link #NO_MATCHING_EXCEPTION_HANDLER_METHOD} if none.
     */
    private Method getMappedMethod(Class<? extends Throwable> exceptionType) {
        List<Class<? extends Throwable>> matches = new ArrayList<>();
        for (Class<? extends Throwable> mappedException : this.mappedMethods.keySet()) {
            if (mappedException.isAssignableFrom(exceptionType)) {
                matches.add(mappedException);
            }
        }
        if (!matches.isEmpty()) {
            if (matches.size() > 1) {
                matches.sort(new ExceptionDepthComparator(exceptionType));
            }
            return this.mappedMethods.get(matches.get(0));
        } else {
            return NO_MATCHING_EXCEPTION_HANDLER_METHOD;
        }
    }

    /**
     * For the {@link #NO_MATCHING_EXCEPTION_HANDLER_METHOD} constant.
     */
    @SuppressWarnings("unused")
    private void noMatchingExceptionHandler() {
    }

}
