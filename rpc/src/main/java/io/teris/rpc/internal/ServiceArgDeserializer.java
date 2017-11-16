/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.Context;
import io.teris.rpc.Deserializer;
import io.teris.rpc.InvocationException;
import io.teris.rpc.Name;


public class ServiceArgDeserializer {

	private static class Typedef extends HashMap<String, Serializable> {}

	@Nonnull
	public CompletableFuture<Object[]> deserialize(Deserializer deserializer, @Nonnull Context context, @Nonnull Method method, @Nullable byte[] data) throws InvocationException {
		List<Object> initial = Arrays.stream(method.getParameters())
			.map(it -> null)
			.collect(Collectors.toList());
		initial.set(0, context);

		if (data == null || data.length == 0) {
			return CompletableFuture.completedFuture(initial.toArray());
		}

		CompletableFuture<Object[]> result = new CompletableFuture<>();
		deserializer.<HashMap<String, Serializable>>deserialize(data, Typedef.class.getGenericSuperclass())
			.thenAccept((rawArgs) -> {

				ConcurrentHashMap<String, Object> argMap = new ConcurrentHashMap<>();
				List<CompletableFuture<Void>> argPromises = new ArrayList<>();
				for (int i = 1; i < method.getParameterCount(); i++) {
					Parameter param = method.getParameters()[i];
					Name nameAnnot = param.getAnnotation(Name.class); // validated non-null on binding
					if (rawArgs.containsKey(nameAnnot.value())) {
						byte[] paramData = (byte[]) rawArgs.remove(nameAnnot.value()); // requirement on deserializer: Serializable -> byte[]
						if (paramData != null) {
							CompletableFuture<Void> argPromise =
								deserializer.deserialize(paramData, param.getParameterizedType())
									.thenAccept((s) -> argMap.put(nameAnnot.value(), s));
							argPromises.add(argPromise);
						}
					}
				}
				if (rawArgs.size() > 0) {
					result.completeExceptionally(new InvocationException(method,
						String.format("too many arguments, %d instead of %s",
							Integer.valueOf(rawArgs.size() + method.getParameterCount() + 1), Integer.valueOf(method.getParameterCount()))));
					return;

				}

				CompletableFuture
					.allOf(argPromises.toArray(new CompletableFuture[]{}))
					.whenComplete((v, t) -> {
						if (t != null) {
							result.completeExceptionally(t);
						}
						else {
							for (int i = 1; i < method.getParameterCount(); i++) {
								Parameter param = method.getParameters()[i];
								Name nameAnnot = param.getAnnotation(Name.class); // validated on binding
								if (nameAnnot != null) {
									initial.set(i, argMap.get(nameAnnot.value()));
								}
							}
							result.complete(initial.toArray());
						}
					});
			});
		return result;
	}
}
