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

package org.jboss.services.example;

import org.jboss.services.ServiceFactory;
import org.jboss.services.example.spi.PropertyResolver;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Main {

    public static void main(final String[] args) throws Exception {
        final PropertyResolver resolver = ServiceFactory.getInstance(PropertyResolver.class);
        System.out.printf("Implementation: %s%n%n", resolver.getClass().getName());

        System.out.printf("%s (%s) - %s%n", resolver.resolve("os.name"), resolver.resolve("os.version"), resolver.resolve("os.arch"));

        System.out.printf("version \"%s\"%n", resolver.resolve("java.version"));
        System.out.printf("%s (%s)%n", resolver.resolve("java.runtime.name"), resolver.resolve("java.runtime.version"));

        System.out.printf("%s (build %s, %s)%n", resolver.resolve("java.vm.name"), resolver.resolve("java.vm.version"), resolver.resolve("java.vm.info"));
    }
}
