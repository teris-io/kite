/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.teris.rpc.Context;
import io.teris.rpc.Deserializer;
import io.teris.rpc.InvocationException;
import io.teris.rpc.Name;


public class ServiceArgDeserializer {

	private static class Typedef extends LinkedHashMap<String, Serializable> {}

	@Nullable
	public Object[] deserialize(Deserializer deserializer, @Nonnull Context context, @Nonnull Method method, @Nullable byte[] data) throws InvocationException {
		List<Object> res = Arrays.stream(method.getParameters())
			.map(it -> null)
			.collect(Collectors.toList());
		res.set(0, context);

		if (data == null || data.length == 0) {
			return res.toArray();
		}

		LinkedHashMap<String, Serializable> rawArgMap = deserializer.deserialize(data, Typedef.class.getGenericSuperclass());
		for (int i = 1; i < method.getParameterCount(); i++) {
			Parameter param = method.getParameters()[i];
			Name nameAnnot = param.getAnnotation(Name.class); // validated on binding
			Object arg = null;
			if (nameAnnot != null) {
				byte[] paramData = (byte[]) rawArgMap.remove(nameAnnot.value()); // requirement on deserializer: Serializable -> byte[]
				if (paramData != null) {
					arg = deserializer.deserialize(paramData, param.getParameterizedType());
				}
			}
			res.set(i, arg);
		}
		if (!rawArgMap.isEmpty()) {
			throw new InvocationException(method, String.format("payload fields %s do not match service signature",
				rawArgMap.keySet()));
		}
		return res.toArray();
	}

}
