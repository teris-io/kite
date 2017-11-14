/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */
package io.teris.rpc.serialization.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Serializer;


public class JsonSerializerTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private final Serializer serializer = new JsonSerializer();

	static class NoDefConstructor implements Serializable {
		private static final long serialVersionUID = -210135588896687017L;

		public int code;

		public String name;

		public double value = 25.0;

		NoDefConstructor(int code, String name) {
			this.code = code;
			this.name = name;
		}
	}

	static class WithArray implements Serializable {
		private static final long serialVersionUID = 5491293815791739629L;
		public ArrayList<String> values;
	}

	static class MissingVals implements Serializable {
		private static final long serialVersionUID = -4487032678271049099L;
		public String name;
	}

	@Test
	public void missingValues_ignoredInDeserialize() {
		MissingVals res = serializer.deserializer().deserialize("{}".getBytes(), MissingVals.class);
		assertNull(res.name);
	}

	@Test
	public void missingList_deserializedAsNull() {
		WithArray res = serializer.deserializer().deserialize("{}".getBytes(), WithArray.class);
		assertNull(res.values);
	}

	@Test
	public void emptyArray_deserializedEmpty() {
		WithArray res = serializer.deserializer().deserialize("{\"values\": []}".getBytes(), WithArray.class);
		assertEquals(Collections.emptyList(), res.values);
	}

	@Test
	public void emptyArray_droppedInSerialization() {
		WithArray res = new WithArray();
		res.values = new ArrayList<>();
		assertEquals("{}", new String(serializer.serialize(res)));
	}

	@Test
	public void missingValues_droppedInSerialization() {
		MissingVals res = new MissingVals();
		assertEquals("{}", new String(serializer.serialize(res)));
	}

	static class WithLocalDateTime implements Serializable {
		public LocalDateTime field;
	}

	@Test
	public void serialize_LocalDateTime_ok() {
		WithLocalDateTime underTest  = new WithLocalDateTime();
		underTest.field = LocalDateTime.of(2016, 2, 29, 12, 34, 56, 234234);
		assertEquals("{\"field\":\"2016-02-29T12:34:56.000234234\"}", new String(serializer.serialize(underTest)));
	}

	@Test
	public void deserialize_LocalDateTime_ok() {
		WithLocalDateTime actual = serializer.deserializer().deserialize("{\"field\":\"2016-02-29T12:34:56.000234234\"}".getBytes(), WithLocalDateTime.class);
		assertEquals(LocalDateTime.of(2016, 2, 29, 12, 34, 56, 234234), actual.field);
	}

	@Test
	public void deserialize_LocalDateTimeShortFormat_ok() {
		WithLocalDateTime actual = serializer.deserializer().deserialize("{\"field\":\"2016-02-29T12:34:56\"}".getBytes(), WithLocalDateTime.class);
		assertEquals(LocalDateTime.of(2016, 2, 29, 12, 34, 56, 0), actual.field);
	}

	static class WithZonedDateTime implements Serializable {
		public ZonedDateTime field;
	}

	@Test
	public void deserialize_zonedDateTime_okWithTimeZone() {
		WithZonedDateTime actual = serializer.deserializer().deserialize("{\"field\":\"2016-02-29T12:34:56Z\"}".getBytes(), WithZonedDateTime.class);
		assertEquals(ZonedDateTime.of(LocalDateTime.of(2016, 2, 29, 12, 34, 56, 0), ZoneId.of("UTC")), actual.field);
	}

	@Test
	public void deserialize_zonedDateTime_throwsWithoutTimeZone() {
		exception.expect(RuntimeException.class);
		exception.expectMessage("Text '2016-02-29T12:34:56' could not be parsed at index 19");
		serializer.deserializer().deserialize("{\"field\":\"2016-02-29T12:34:56\"}".getBytes(), WithZonedDateTime.class);
	}

	private static class TypedefOuter extends HashMap<String, Serializable> {}

	private static class TypedefInner extends HashSet<LocalDateTime> {}

	@Test
	public void deserialize_Serializable_asByteArrray() {
		HashMap<String, Serializable> data = new HashMap<>();

		HashSet<LocalDateTime> dates = new HashSet<>();
		LocalDateTime date1 = LocalDateTime.of(2016, 2, 29, 12, 34, 56);
		dates.add(date1);
		LocalDateTime date2 = LocalDateTime.of(2016, 2, 28, 12, 34, 56);
		dates.add(date2);
		data.put("dates", dates);
		byte[] payload = serializer.serialize(data);

		HashMap<String, Serializable> actual = serializer.deserializer().deserialize(payload, TypedefOuter.class.getGenericSuperclass());
		Set<LocalDateTime> actualDates = serializer.deserializer().deserialize((byte[]) actual.get("dates"), TypedefInner.class.getGenericSuperclass());
		assertEquals(dates, actualDates);
	}
}
