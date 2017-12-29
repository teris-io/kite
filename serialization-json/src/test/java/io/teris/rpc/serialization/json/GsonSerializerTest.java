/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.rpc.serialization.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;


public class GsonSerializerTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private final Serializer serializer = GsonSerializer.builder().build();
	
	private final Deserializer deserializer = serializer.deserializer();

	static class WithArray implements Serializable {
		private static final long serialVersionUID = 5491293815791739629L;
		public ArrayList<String> values;
	}

	static class MissingVals implements Serializable {
		private static final long serialVersionUID = -4487032678271049099L;
		public String name;
	}

	@Test
	public void missingValues_ignoredInDeserialize() throws Exception {
		MissingVals res = deserializer.deserialize("{}".getBytes(), MissingVals.class).get();
		assertNull(res.name);
	}

	@Test
	public void missingList_deserializedAsNull() throws Exception {
		WithArray res = deserializer.deserialize("{}".getBytes(), WithArray.class).get();
		assertNull(res.values);
	}

	@Test
	public void emptyArray_deserializedEmpty() throws Exception {
		WithArray res = deserializer.deserialize("{\"values\": []}".getBytes(), WithArray.class).get();
		assertEquals(Collections.emptyList(), res.values);
	}

	@Test
	public void emptyArray_preservedInSerialization() throws Exception {
		WithArray res = new WithArray();
		res.values = new ArrayList<>();
		assertEquals("{\"values\":[]}", new String(serializer.serialize(res).get()));
	}

	public enum TestEnum { one, two;	}

	@Test
	public void enum_serializedAsStrings() throws Exception {
		assertEquals("\"one\"", new String(serializer.serialize(TestEnum.one).get()));
	}

	@Test
	public void enum_deserializedFromStrings() throws Exception {
		assertEquals(TestEnum.two, deserializer.deserialize("two".getBytes(), TestEnum.class).get());
	}

	@Test
	public void missingValues_droppedInSerialization() throws Exception {
		MissingVals res = new MissingVals();
		assertEquals("{}", new String(serializer.serialize(res).get()));
	}

	static class WithLocalDateTime implements Serializable {
		public LocalDateTime field;
	}

	@Test
	public void serialize_LocalDateTime_ok() throws Exception {
		WithLocalDateTime underTest  = new WithLocalDateTime();
		underTest.field = LocalDateTime.of(2016, 2, 29, 12, 34, 56, 234234);
		assertEquals("{\"field\":\"2016-02-29T12:34:56.000234234\"}", new String(serializer.serialize(underTest).get()));
	}

	@Test
	public void deserialize_LocalDateTime_ok() throws Exception {
		WithLocalDateTime actual = deserializer.deserialize("{\"field\":\"2016-02-29T12:34:56.000234234\"}".getBytes(), WithLocalDateTime.class).get();
		assertEquals(LocalDateTime.of(2016, 2, 29, 12, 34, 56, 234234), actual.field);
	}

	@Test
	public void deserialize_LocalDateTimeShortFormat_ok() throws Exception {
		WithLocalDateTime actual = deserializer.deserialize("{\"field\":\"2016-02-29T12:34:56\"}".getBytes(), WithLocalDateTime.class).get();
		assertEquals(LocalDateTime.of(2016, 2, 29, 12, 34, 56, 0), actual.field);
	}

	static class WithZonedDateTime implements Serializable {
		public ZonedDateTime field;
	}

	@Test
	public void deserialize_zonedDateTime_okWithTimeZone() throws Exception {
		WithZonedDateTime actual = deserializer.deserialize("{\"field\":\"2016-02-29T12:34:56Z\"}".getBytes(), WithZonedDateTime.class).get();
		assertEquals(ZonedDateTime.of(LocalDateTime.of(2016, 2, 29, 12, 34, 56, 0), ZoneId.of("Z")), actual.field);
	}

	@Test
	public void deserialize_zonedDateTime_throwsWithoutTimeZone() throws Exception {
		exception.expect(ExecutionException.class);
		exception.expectMessage("Text '2016-02-29T12:34:56' could not be parsed at index 19");
		deserializer.deserialize("{\"field\":\"2016-02-29T12:34:56\"}".getBytes(), WithZonedDateTime.class).get();
	}

	private static class TypedefOuter extends HashMap<String, Serializable> {}

	private static class TypedefInner extends HashSet<LocalDateTime> {}

	@Test
	public void deserialize_Serializable_asByteArrray() throws Exception {
		HashMap<String, Serializable> data = new HashMap<>();

		HashSet<LocalDateTime> dates = new HashSet<>();
		LocalDateTime date1 = LocalDateTime.of(2016, 2, 29, 12, 34, 56);
		dates.add(date1);
		LocalDateTime date2 = LocalDateTime.of(2016, 2, 28, 12, 34, 56);
		dates.add(date2);
		data.put("dates", dates);
		byte[] payload = serializer.serialize(data).get();

		CompletableFuture<HashMap<String, Serializable>> actual = deserializer.deserialize(payload, TypedefOuter.class.getGenericSuperclass());
		CompletableFuture<HashSet<LocalDateTime>> actualDates = deserializer.deserialize((byte[]) actual.get().get("dates"), TypedefInner.class.getGenericSuperclass());
		assertEquals(dates, actualDates.get());
	}

	@Test
	public void roundtrip_iso8859_1_ok() throws Exception {
		String value = "äüöабв";
		Serializer serializer = GsonSerializer.builder().withCharset(StandardCharsets.ISO_8859_1).build();
		byte[] data = serializer.serialize(value).get(5, TimeUnit.SECONDS);
		assertEquals(8, data.length);
		assertEquals("äüö???", serializer.deserializer().deserialize(data, String.class).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void roundtrip_utf8_asDefault_ok() throws Exception {
		String value = "äüöабв";
		Serializer serializer = GsonSerializer.builder().build();
		byte[] data = serializer.serialize(value).get(5, TimeUnit.SECONDS);
		assertEquals(14, data.length);
		assertEquals(value, serializer.deserializer().deserialize(data, String.class).get(5, TimeUnit.SECONDS));
	}
}
