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

package org.jboss.services.annotation.processor;

import static org.jboss.jdeparser.JExprs.$;
import static org.jboss.jdeparser.JTypes._;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.jboss.services.annotation.ServiceProvider;
import org.jboss.jdeparser.FormatPreferences;
import org.jboss.jdeparser.JBlock;
import org.jboss.jdeparser.JBlock.Braces;
import org.jboss.jdeparser.JClassDef;
import org.jboss.jdeparser.JDeparser;
import org.jboss.jdeparser.JExpr;
import org.jboss.jdeparser.JExprs;
import org.jboss.jdeparser.JFiler;
import org.jboss.jdeparser.JIf;
import org.jboss.jdeparser.JMethodDef;
import org.jboss.jdeparser.JMod;
import org.jboss.jdeparser.JSourceFile;
import org.jboss.jdeparser.JSources;
import org.jboss.jdeparser.JType;
import org.jboss.jdeparser.JVarDeclaration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServiceProviderProcessor extends AbstractProcessor {

    private static final DateTimeFormatter ISO_8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(ServiceProvider.class.getName());
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {

        // We only want to process @ServiceProvider types
        final TypeElement annotation = elementUtil.getTypeElement(ServiceProvider.class.getName());
        if (annotations.contains(annotation)) {
            final Map<CharSequence, Set<CharSequence>> servicesMap = new HashMap<>();
            // Get all the classes annotated with @ServiceProvider
            final Set<? extends TypeElement> implementations = ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(annotation));
            for (TypeElement impl : implementations) {
                // Get the annotation
                final TypeElement contract = resolveClass(impl);
                if (isValid(impl, contract)) {
                    final CharSequence contractName = elementUtil.getBinaryName(contract);
                    final Set<CharSequence> impls;
                    if (servicesMap.containsKey(contractName)) {
                        impls = servicesMap.get(contractName);
                    } else {
                        impls = new LinkedHashSet<>();
                        servicesMap.put(contractName, impls);
                        // Generate the factory if required
                        final ServiceProvider serviceProvider = impl.getAnnotation(ServiceProvider.class);
                        if (serviceProvider.generateFactory()) {
                            generateFactorySource(filer, contract);
                        }
                    }
                    impls.add(elementUtil.getBinaryName(impl));
                }
            }

            // Check for an existing file
            for (Entry<CharSequence, Set<CharSequence>> entry : servicesMap.entrySet()) {
                try {
                    final FileObject fileObject = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + entry.getKey());
                    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(fileObject.openInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            entry.getValue().add(line);
                        }
                    }
                } catch (FileNotFoundException ignore) {
                    // File was not found, we can ignore this
                } catch (IOException e) {
                    printError(e);
                }
            }

            for (Entry<CharSequence, Set<CharSequence>> entry : servicesMap.entrySet()) {
                try {
                    final FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + entry.getKey());
                    try (final PrintWriter writer = new PrintWriter(new OutputStreamWriter(fileObject.openOutputStream(), StandardCharsets.UTF_8))) {
                        for (CharSequence s : entry.getValue()) {
                            writer.println(s.toString());
                        }
                    }
                } catch (IOException e) {
                    printError(e);
                }
            }
        }
        return false;
    }

    private boolean isValid(final TypeElement impl, final TypeElement contract) {
        if (impl.getKind() != ElementKind.CLASS || impl.getModifiers().contains(Modifier.ABSTRACT)) {
            printError(impl, "%s must be a concrete class", impl.getQualifiedName());
            return false;
        } else if (contract == null) {
            printError(impl, "Missing required value argument");
            return false;
            // Validate the class implements or extends the service
        } else if (!typeUtil.isAssignable(impl.asType(), contract.asType())) {
            printError(impl, "Type %s is not assignable from %s", elementUtil.getBinaryName(impl), elementUtil.getBinaryName(contract));
            return false;
        }
        return true;
    }

    private TypeElement resolveClass(final AnnotatedConstruct type) {
        final AnnotationMirror mirror = getAnnotation(ServiceProvider.class, type);
        if (mirror != null) {
            final AnnotationValue value = getAnnotationValue(mirror);
            if (value != null) {
                return toElement(value);
            }
        }
        return null;
    }

    private void generateFactorySource(final Filer filer, final TypeElement type) {
        // Set up the names
        final String packageName = elementUtil.getPackageOf(type).toString();
        final String factoryName = type.getSimpleName() + "Factory";
        final String serviceClassName = type.getQualifiedName().toString();

        // Create the class definition
        final JSources sources = JDeparser.createSources(JFiler.newInstance(filer), new FormatPreferences(new Properties()));
        final JSourceFile sourceFile = sources.createSourceFile(packageName, factoryName);
        final JClassDef classDef = sourceFile._class(JMod.PUBLIC, factoryName);

        // Imports
        sourceFile._import(ServiceLoader.class);
        sourceFile._import(Generated.class);

        // Add the @Generated annotation to the class
        classDef.annotate(Generated.class)
                .value("value", getClass().getName())
                .value("date", JExprs.str(ZonedDateTime.now().format(ISO_8601)));

        // Create the types needed
        final JType serviceClassType = _(serviceClassName);
        final JType serviceLoaderType = _(ServiceLoader.class).typeArg(serviceClassType);

        // Create a static instance of the field
        final JVarDeclaration instance = classDef.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, serviceClassType, "INSTANCE");

        // A static initializer
        final JBlock staticInit = classDef.staticInit();

        // Create the ServiceLoader variable in the body
        final JVarDeclaration loader = staticInit.var(JMod.FINAL, serviceLoaderType, "loader", serviceLoaderType.call("load").arg(serviceClassType._class()));

        // If the loader's iterator has a next, return it otherwise return null
        final JIf decision = staticInit._if($(loader).call("iterator").call("hasNext"));
        decision.block(Braces.REQUIRED).assign($(instance), $(loader).call("iterator").call("next"));
        decision._else().block(Braces.REQUIRED).assign($(instance), JExpr.NULL);

        // Create a static method
        final JMethodDef getInstance = classDef.method(JMod.PUBLIC | JMod.STATIC, serviceClassName, "getInstance");
        // Create the body of the method
        getInstance.body()._return($(instance));

        // Write the source file
        try {
            sources.writeSources();
        } catch (IOException e) {
            printError(type, "Error generating factory class: %s", e);
        }
    }
}
