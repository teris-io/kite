/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.serialization.json;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;


public class GsonSerializer implements Serializer {

	private static final String CONTENT_TYPE = "application/json";

	private final Deserializer deserializer;

	private final Gson gson;

	public GsonSerializer(GsonBuilder builder) {
		gson = builder.create();
		deserializer = new GsonDeserializer(builder);
	}

	public static GsonSerializerBuilder builder() {
		return new GsonSerializerBuilder();
	}

	@Nonnull
	@Override
	public <CT extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull CT value) {
		return CompletableFuture.supplyAsync(() -> gson.toJson(value).getBytes());
	}

	@Nonnull
	@Override
	public String contentType() {
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public Deserializer deserializer() {
		return deserializer;
	}
}
