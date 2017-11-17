/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public class Context implements Map<String, String> {

	public static final String REQUEST_ID_KEY = "X-Request-ID";

	public static final String CONTENT_TYPE_KEY = "Content-Type";


	public static volatile Supplier<String> uniqueIdGenerator = () -> String.valueOf(System.nanoTime());


	private static final String DEFAULT_CONTENT_TYPE = "application/json";

	private final ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();


	public Context() {
		this(null);
	}

	public Context(@Nullable Context context) {
		this((Map<String, String>) context);
	}

	public Context(@Nullable Map<String, String> context) {
		if (context != null) {
			data.putAll(context);
		}
		put(REQUEST_ID_KEY, uniqueIdGenerator.get());
		if (!containsKey(CONTENT_TYPE_KEY) || "".equals(data.get(CONTENT_TYPE_KEY).trim())) {
			data.put(CONTENT_TYPE_KEY, DEFAULT_CONTENT_TYPE);
		}
	}

	@Override
	public int size() {
		return data.size();
	}

	@Override
	public boolean isEmpty() {
		return data.isEmpty();
	}

	@Override
	public boolean containsKey(@Nonnull Object key) {
		return data.containsKey(key);
	}

	@Override
	public boolean containsValue(@Nonnull Object value) {
		return data.containsValue(value);
	}

	@Override
	public String get(@Nonnull Object key) {
		return data.get(key);
	}

	@Override
	public String put(@Nonnull String key, @Nonnull String value) {
		return data.put(key, value);
	}

	@Override
	public String remove(@Nonnull Object key) {
		return data.remove(key);
	}

	@Override
	public void putAll(@Nonnull Map<? extends String, ? extends String> map) {
		data.putAll(map);
	}

	@Override
	public void clear() {
		data.clear();
	}

	@Nonnull
	@Override
	public Set<String> keySet() {
		return data.keySet();
	}

	@Nonnull
	@Override
	public Collection<String> values() {
		return data.values();
	}

	@Nonnull
	@Override
	public Set<Entry<String, String>> entrySet() {
		return data.entrySet();
	}

	@Nonnull
	public HashMap<String, String> hashMap() {
		return new HashMap<>(data);
	}
}
