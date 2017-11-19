/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.testfixture;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import com.google.gson.Gson;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;


public class TestSerializer implements Serializer {

	private static final String CONTENT_TYPE = "application/json";

	private final Gson gson = new Gson();

	private final Deserializer deserializer = new TestDeserializer();

	@Nonnull
	@Override
	public String contentType() {
		return CONTENT_TYPE;
	}

	@Nonnull
	@Override
	public <V extends Serializable> CompletableFuture<byte[]> serialize(@Nonnull V value) {
		return CompletableFuture.supplyAsync(() -> gson.toJson(value).getBytes());
	}

	@Nonnull
	@Override
	public Deserializer deserializer() {
		return deserializer;
	}
}
