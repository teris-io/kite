/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.serialization.json;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.teris.rpc.Deserializer;


public class GsonDeserializer implements Deserializer {

	private final Gson gson;

	private final Charset charset;

	public GsonDeserializer(GsonBuilder builder) {
		this(builder, StandardCharsets.UTF_8);
	}

	public GsonDeserializer(GsonBuilder builder, Charset charset) {
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

	private static class SerializableDeserializer implements JsonDeserializer<Serializable> {

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
