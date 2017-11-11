/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.teris.rpc.ExportName;
import io.teris.rpc.ExportPath;


/**
 * Defines the function to extract and validate the service method route used e.g.
 * for identifying the service on the network.
 */
public class ServiceRouteFunc implements Function<Method, String> {

	private static final String SERVICE_SUFFIX = "Service";

	@Nonnull
	@Override
	public String apply(@Nonnull Method method) {
		String packageRoute = packageRoute(method);
		String serviceRoute = serviceRoute(method);
		String methodRoute = methodRoute(method);
		String res = sanitize(packageRoute + "." + serviceRoute + "." + methodRoute);
		if ("".equals(res)) {
			throw new IllegalArgumentException(String.format("Empty root for %s.%s",
				method.getDeclaringClass().getSimpleName(), method.getName()));
		}
		return res;
	}

	private String packageRoute(Method method) {
		Class<?> methodClass = method.getDeclaringClass();

		String packageRoute = methodClass.getCanonicalName();
		packageRoute = packageRoute.substring(0, packageRoute.lastIndexOf(methodClass.getSimpleName()) - 1);

		ExportPath exportPathAnnot = methodClass.getAnnotation(ExportPath.class);
		if (exportPathAnnot != null) {
			String value = exportPathAnnot.value().trim();
			String replace = exportPathAnnot.replace().trim();

			if (!"".equals(replace)) {
				packageRoute = packageRoute.replace(replace, value);
			}
			else if (!"".equals(value)) {
				packageRoute = value;
			}
		}
		return packageRoute;
	}

	private String serviceRoute(Method method) {
		ExportName exportName = method.getDeclaringClass().getAnnotation(ExportName.class);
		if (exportName != null && !"".equals(exportName.value().trim())) {
			return exportName.value().trim();
		}
		String serviceRoute = method.getDeclaringClass().getSimpleName();
		if (serviceRoute.endsWith(SERVICE_SUFFIX)) {
			serviceRoute = serviceRoute.substring(0, serviceRoute.lastIndexOf(SERVICE_SUFFIX));
		}
		return serviceRoute;
	}

	private String methodRoute(Method method) {
		ExportName exportName = method.getAnnotation(ExportName.class);
		if (exportName != null) {
			return exportName.value().trim();
		}
		return method.getName();
	}

	private String sanitize(String route) {
		StringBuilder sb = new StringBuilder();
		AtomicBoolean initialized = new AtomicBoolean(false);
		String[] parts = route.replaceAll("\\$", ".").split("\\.");
		for (String part: parts) {
			String elm = part.trim();
			if (!"".equals(elm)) {
				if (initialized.getAndSet(true)) {
					sb.append(".");
				}
				sb.append(elm);
			}
		}
		return sb.toString().toLowerCase();
	}
}
