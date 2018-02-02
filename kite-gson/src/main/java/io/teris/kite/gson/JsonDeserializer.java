/*
 * Copyright (c) Oleg Sklyar & teris.io, 2018. All rights reserved.
 */

package io.teris.kite.gson;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.teris.kite.Deserializer;


public class JsonDeserializer implements Deserializer {

	private final Gson gson;

	private final Charset charset;

	public JsonDeserializer(GsonBuilder builder) {
		this(builder, StandardCharsets.UTF_8);
	}

	public JsonDeserializer(GsonBuilder builder, Charset charset) {
		gson = builder
			.registerTypeAdapter(Serializable.class, new SerializableDeserializer(charset))
			.create();
		this.charset = charset;
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz) {
		return CompletableFuture.supplyAsync(() -> gson.fromJson(new String(data, charset), clazz));
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Type type) {
		return CompletableFuture.supplyAsync(() -> gson.fromJson(new String(data, charset), type));
	}

	private static class SerializableDeserializer implements com.google.gson.JsonDeserializer<Serializable> {

		private final Charset charset;

		SerializableDeserializer(Charset charset) {
			this.charset = charset;
		}

		@Override
		public Serializable deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return json.toString().getBytes(charset);
		}
	}
}
