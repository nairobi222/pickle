/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fourlastor.pickle;

import org.gradle.api.GradleException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Copied from
 *
 * https://github.com/gradle/gradle/blob/v5.1.1/subprojects/base-services/src/main/java/org/gradle/internal/reflect/JavaMethod.java
 */
public class JavaMethod<T, R> {
    private final Method method;
    private final Class<R> returnType;

    public JavaMethod(Class<T> target, Class<R> returnType, String name, boolean allowStatic, Class<?>... paramTypes) throws NoSuchMethodException {
        this(returnType, findMethod(target, target, name, allowStatic, paramTypes));
    }

    public JavaMethod(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        this(target, returnType, name, false, paramTypes);
    }

    public JavaMethod(Class<R> returnType, Method method) {
        this.returnType = returnType;
        this.method = method;
        method.setAccessible(true);
    }

    private static Method findMethod(Class origTarget, Class target, String name, boolean allowStatic, Class<?>[] paramTypes) throws NoSuchMethodException {
        for (Method method : target.getDeclaredMethods()) {
            if (!allowStatic && Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), paramTypes)) {
                return method;
            }
        }

        Class<?> parent = target.getSuperclass();
        if (parent == null) {
            throw new NoSuchMethodException(String.format("Could not find method %s on %s.", name, origTarget.getSimpleName()));
        } else {
            return findMethod(origTarget, parent, name, allowStatic, paramTypes);
        }
    }

    public boolean isStatic() {
        return Modifier.isStatic(method.getModifiers());
    }

    public R invokeStatic(Object... args) {
        return invoke(null, args);
    }

    public R invoke(T target, Object... args) {
        try {
            Object result = method.invoke(target, args);
            return returnType.cast(result);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), target), e);
        }
    }

    public Method getMethod() {
        return method;
    }

    public Class<?>[] getParameterTypes(){
        return method.getParameterTypes();
    }


    public static <T, R> JavaMethod<T, R> method(T target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        Class<T> targetClass = (Class<T>) target.getClass();
        return new JavaMethod<>(targetClass, returnType, name, paramTypes);
    }
}
