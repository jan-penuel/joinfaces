/*
 * Copyright 2016-2016 the original author or authors.
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

package org.joinfaces;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.faces.bean.NoneScoped;
import javax.faces.bean.RequestScoped;
import javax.faces.bean.SessionScoped;
import javax.faces.bean.ViewScoped;
import javax.servlet.annotation.HandlesTypes;

import lombok.Builder;
import lombok.NonNull;

import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory of classes with jsf types handled by servlet context initializer.
 * @author Marcelo Fernandes
 */
@Builder
public class JsfClassFactory {

	private static final Logger log = LoggerFactory
		.getLogger(JsfClassFactory.class);

	@NonNull
	private JsfClassFactoryConfiguration jsfAnnotatedClassFactoryConfiguration;

	/**
	 * Ignore ViewScoped, SessionScoped, RequestScoped and NoneScoped annotations
	 * because spring will take care of them.
	 * @return set of annotations to exclude from handlesType
	 */
	private Set<Class<? extends Annotation>> annotationsToExclude() {
		Set<Class<? extends Annotation>> result = new HashSet<Class<? extends Annotation>>();
		if (this.jsfAnnotatedClassFactoryConfiguration.isExcludeScopedAnnotations()) {
			result.add(ViewScoped.class);
			result.add(SessionScoped.class);
			result.add(RequestScoped.class);
			result.add(NoneScoped.class);
		}
		return result;
	}

	/**
	 * Compute types handled ( set of annotation classes and set of other classes )
	 * handled by servlet container initializer.
	 * @return types to be handled by jsf implementation
	 */
	private TypesHandled handlesTypes() {
		TypesHandled result = new TypesHandled();

		HandlesTypes ht = null;
		if (this.jsfAnnotatedClassFactoryConfiguration.getServletContainerInitializer() != null) {
			ht = this.jsfAnnotatedClassFactoryConfiguration.getServletContainerInitializer().getClass().getAnnotation(HandlesTypes.class);
		}
		if (ht != null) {
			Set<Class<? extends Annotation>> annotationsToExclude = annotationsToExclude();

			for (Class<?> type : ht.value()) {
				if (type.isAnnotation()) {
					Class<? extends Annotation> annotation = (Class<? extends Annotation>) type;
					if (!annotationsToExclude.contains(annotation)) {
						result.getAnnotationTypes().add(annotation);
					}
				}
				else {
					result.getOtherTypes().add(type);
				}
			}
		}

		return result;
	}

	/**
	 * Compute types to be handled: set of annotation classes to be handled by
	 * servlet container initializer and set of other classes to be handled by
	 * servlet container initializer.
	 * Search libraries with anotherFacesConfig also.
	 * @return classes annotated by types handled by servlet container initializer.
	 */
	public Set<Class<?>> find() {
		Set<Class<?>> result = new HashSet<Class<?>>();

		TypesHandled handlesTypes = handlesTypes();
		// check if any type is handled
		if (!handlesTypes.isEmpty()) {
			// get only urls of libraries that contains jsf types
			Collection<URL> urls = new HashSet<URL>(ClasspathHelper.forResource("META-INF/faces-config.xml", this.getClass().getClassLoader()));
			// add jsf library with anotherFacesConfig
			String anotherFacesConfig = this.jsfAnnotatedClassFactoryConfiguration.getAnotherFacesConfig();
			if (anotherFacesConfig != null) {
				urls.addAll(ClasspathHelper.forResource(anotherFacesConfig, this.getClass().getClassLoader()));
			}

			// create reflections
			Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(urls));

			// add types annotated for each type to be handled
			for (Class<? extends Annotation> annotationType : handlesTypes.getAnnotationTypes()) {
				result.addAll(reflections.getTypesAnnotatedWith(annotationType));
			}
			// add subtype of other types to be handled
			for (Class<?> otherType : handlesTypes.getOtherTypes()) {
				result.addAll(reflections.getSubTypesOf(otherType));
			}
		}

		return result;
	}
}
