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

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

/**
 * An local abstract processor that includes some helper utilities.
 * <p/>
 * <b>Note:</b> The protected global variables should only be accessed from the {@link #process(java.util.Set,
 * javax.annotation.processing.RoundEnvironment)} method. These variables are initialized in the {@link
 * #init(javax.annotation.processing.ProcessingEnvironment)} method and are subject to the contract set forth by the
 * {@link javax.annotation.processing.Processor}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractProcessor extends javax.annotation.processing.AbstractProcessor {

    private Messager messager;
    protected Elements elementUtil;
    protected Types typeUtil;
    protected Filer filer;
    protected Map<String, String> options;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtil = processingEnv.getElementUtils();
        typeUtil = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        options = processingEnv.getOptions();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        final SourceVersion result;
        final SupportedSourceVersion version = getClass().getAnnotation(SupportedSourceVersion.class);
        if (version != null) {
            result = version.value();
        } else {
            result = SourceVersion.latestSupported();
            // Must at least return 6
            if (result == SourceVersion.RELEASE_5) {
                return SourceVersion.RELEASE_6;
            }
        }
        return result;
    }

    /**
     * Prints an error message.
     *
     * @param t the exception to be printed
     */
    protected void printError(final Throwable t) {
        messager.printMessage(Kind.ERROR, t.getLocalizedMessage());
    }

    /**
     * Prints an error message.
     *
     * @param e   the element the error occurred on
     * @param msg the message to print
     */
    protected void printError(final Element e, final CharSequence msg) {
        messager.printMessage(Kind.ERROR, msg, e);
    }

    /**
     * Prints an error message
     *
     * @param e      the element the error occurred on
     * @param format the format for the message
     * @param args   the arguments for the format
     */
    protected void printError(final Element e, final String format, final Object... args) {
        messager.printMessage(Kind.ERROR, String.format(format, args), e);
    }

    /**
     * Transforms the annotation value into an element.
     *
     * @param value the annotation value
     *
     * @return the value of the annotation as an element
     */
    @SuppressWarnings("unchecked")
    protected <T extends Element> T toElement(final AnnotationValue value) {
        final TypeMirror valueMirror = (TypeMirror) value.getValue();
        return (T) typeUtil.asElement(valueMirror);
    }

    /**
     * Gets an annotation, if present, from the type.
     *
     * @param annotation the annotation to retrieve
     * @param element    the element the annotation may be present on
     *
     * @return the annotation or {@code null} if the annotation is not present
     */
    static AnnotationMirror getAnnotation(final Class<? extends Annotation> annotation, final Element element) {
        final String name = annotation.getName();
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(name)) {
                return mirror;
            }
        }
        return null;
    }

    /**
     * Gets the value of the annotation.
     *
     * @param annotation the annotation to retrieve the value from
     *
     * @return the value if defined otherwise {@code null}
     */
    static AnnotationValue getAnnotationValue(final AnnotationMirror annotation) {
        return getAnnotationValue(annotation, "value");
    }

    /**
     * Gets the value of the named attribute for the annotation.
     *
     * @param annotation the annotation to retrieve the value from
     * @param name       the name of the attribute for the value
     *
     * @return the value if defined otherwise {@code null}
     */
    static AnnotationValue getAnnotationValue(final AnnotationMirror annotation, final String name) {
        for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
            if (name.equals(entry.getKey().getSimpleName().toString())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
