/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.serialization.json;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;


public class GsonSerializerBuilder {

	private final GsonBuilder builder = new GsonBuilder()
		.registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
		.registerTypeAdapter(LocalDate.class, new LocalDateDeserializer())
		.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
		.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
		.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer())
		.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeDeserializer())
		.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeSerializer())
		.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeDeserializer());


	GsonSerializerBuilder() {}

	public GsonSerializerBuilder registerTypeAdapter(Type type, Object typeAdapter) {
		builder.registerTypeAdapter(type, typeAdapter);
		return this;
	}

	public GsonBuilder rawBuilder() {
		return builder;
	}

	public GsonSerializer build() {
		return new GsonSerializer(builder);
	}

	private static class LocalDateDeserializer implements JsonDeserializer<LocalDate> {

		@Override
		public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return LocalDate.from(DateTimeFormatter.ISO_DATE.parse(json.getAsString()));
		}
	}

	private static class LocalDateSerializer implements com.google.gson.JsonSerializer<LocalDate> {

		public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
		}
	}

	private static class LocalDateTimeDeserializer implements JsonDeserializer<LocalDateTime> {

		@Override
		public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(json.getAsString()));
		}
	}

	private static class LocalDateTimeSerializer implements com.google.gson.JsonSerializer<LocalDateTime> {

		public JsonElement serialize(LocalDateTime date, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		}
	}

	private static class ZonedDateTimeDeserializer implements JsonDeserializer<ZonedDateTime> {

		@Override
		public ZonedDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return ZonedDateTime.from(DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(json.getAsString()));
		}
	}

	private static class ZonedDateTimeSerializer implements com.google.gson.JsonSerializer<ZonedDateTime> {

		public JsonElement serialize(ZonedDateTime date, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
		}
	}

	private static class OffsetDateTimeDeserializer implements JsonDeserializer<OffsetDateTime> {

		@Override
		public OffsetDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return OffsetDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(json.getAsString()));
		}
	}

	private static class OffsetDateTimeSerializer implements com.google.gson.JsonSerializer<OffsetDateTime> {

		public JsonElement serialize(OffsetDateTime date, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		}
	}
}
