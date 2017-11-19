/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.testfixture;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import io.teris.rpc.Deserializer;


public class TestDeserializer implements Deserializer {

	private final Gson gson = new GsonBuilder()
		.registerTypeAdapter(Serializable.class, new SerializableDeserializer())
		.create();

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Class<CT> clazz) {
		return CompletableFuture.supplyAsync(() -> gson.fromJson(new String(data), clazz));
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<CT> deserialize(@Nonnull byte[] data, @Nonnull Type type) {
		return CompletableFuture.supplyAsync(() -> gson.fromJson(new String(data), type));
	}

	private static class SerializableDeserializer implements com.google.gson.JsonDeserializer<Serializable> {

		@Override
		public Serializable deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return json.toString().getBytes();
		}
	}
}
