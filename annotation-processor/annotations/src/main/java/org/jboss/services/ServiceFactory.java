/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.services;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServiceFactory {

    /**
     * Gets an instance of a server from a factory.
     * <p/>
     * The factory name must be the name of the service with the {@code Factory} appended to the name and it must be in
     * the same package as the service.
     *
     * @param serviceType the type of the service to locate
     * @param <T>         the type
     *
     * @return an implementation of the service from the factory
     *
     * @throws java.lang.RuntimeException if an error occurs attempting create the factory instance or invoke the
     *                                    {@code
     *                                    getInstance()} method
     */
    public static <T> T getInstance(final Class<T> serviceType) {
        final ClassLoader cl = getClassLoader();
        final String name = serviceType.getPackage().getName() + "." + serviceType.getSimpleName() + "Factory";
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            final Class<?> factory = Class.forName(name, true, cl);
            return serviceType.cast(lookup.findStatic(factory, "getInstance", MethodType.methodType(serviceType)).invoke());
        } catch (Throwable e) {
            throw new IllegalArgumentException("Could not find or invoke factory method getInstance()", e);
        }
    }

    private static ClassLoader getClassLoader() {
        ClassLoader result = Thread.currentThread().getContextClassLoader();
        if (result == null) {
            result = ServiceFactory.class.getClassLoader();
        }
        return result;
    }
}
