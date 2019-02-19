/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.gson;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.teris.kite.Deserializer;
import io.teris.kite.Serializer;


public class JsonSerializer implements Serializer {

	private static final String CONTENT_TYPE = "application/json";

	private final Deserializer deserializer;

	private final Gson gson;

	private final Charset charset;

	public JsonSerializer(GsonBuilder builder) {
		this(builder, StandardCharsets.UTF_8);
	}

	public JsonSerializer(GsonBuilder builder, Charset charset) {
		gson = builder.create();
		this.charset = charset;
		deserializer = new JsonDeserializer(builder, charset);
	}

	public static JsonSerializerBuilder builder() {
		return new JsonSerializerBuilder();
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull CT value) {
		return CompletableFuture.supplyAsync(() -> gson.toJson(value).getBytes(charset));
	}

	@Nonnull
	@Override
	public String contentType() {
		if (charset != null) {
			return CONTENT_TYPE + "; charset=" + charset.name();
		}
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public Deserializer deserializer() {
		return deserializer;
	}
}
