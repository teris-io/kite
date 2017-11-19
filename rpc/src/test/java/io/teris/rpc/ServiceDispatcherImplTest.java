/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.testfixture.TestSerializer;


public class ServiceDispatcherImplTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private static final TestSerializer serializer = new TestSerializer();

	private static final Deserializer deserializer = serializer.deserializer();

	private static final Context context = new Context();

	private static Method method;

	@BeforeClass
	public static void init() throws Exception {
		method = AService.class.getMethod("call", Context.class, HashSet.class, HashMap.class);
	}

	@Service
	public interface AService {

		void call(Context context, @Name("keys") HashSet<String> keys, @Name("data") HashMap<String, Integer> data);

		void empty(Context context);
	}

	@Test
	public void deserialize_argsWithGenerics_success() throws Exception {
		ServiceDispatcherImpl underTest = new ServiceDispatcherImpl(Collections.emptyMap(), serializer, Collections.emptyMap());

		HashSet<String> keys = new HashSet<>(Arrays.asList("Ab", "Bc"));
		HashMap<String, Integer> data = new HashMap<>();
		data.put("Cd", Integer.valueOf(25));
		data.put("Dc", Integer.valueOf(31));
		LinkedHashMap<String, Serializable> args = new LinkedHashMap<>();
		args.put("keys", keys);
		args.put("data", data);

		Object[] actual = underTest.deserialize(context, method, serializer.serialize(args).get()).get();
		assertEquals(3, actual.length);
		assertSame(context, actual[0]);
		assertEquals(keys, actual[1]);
		assertEquals(data, actual[2]);
	}

	@Test
	public void deserialize_emptyData_success_nulls() throws Exception {
		ServiceDispatcherImpl underTest = new ServiceDispatcherImpl(Collections.emptyMap(), serializer, Collections.emptyMap());

		Object[] actual = underTest.deserialize(context, method, new byte[]{}).get();
		assertEquals(3, actual.length);
		assertSame(context, actual[0]);
		assertNull(actual[1]);
		assertNull(actual[2]);
	}

	@Test
	public void deserialize_nullData_success_nulls() throws Exception {
		ServiceDispatcherImpl underTest = new ServiceDispatcherImpl(Collections.emptyMap(), serializer, Collections.emptyMap());

		Object[] actual = underTest.deserialize(context, method, null).get();
		assertEquals(3, actual.length);
		assertSame(context, actual[0]);
		assertNull(actual[1]);
		assertNull(actual[2]);
	}

	@Test
	public void deserialize_noParams_emptyData_success_contextOnly() throws Exception {
		ServiceDispatcherImpl underTest = new ServiceDispatcherImpl(Collections.emptyMap(), serializer, Collections.emptyMap());

		Method emptyMethod = AService.class.getMethod("empty", Context.class);

		Object[] actual = underTest.deserialize(context, emptyMethod, null).get();
		assertEquals(1, actual.length);
		assertSame(context, actual[0]);
	}

	@Test
	public void deserialize_missingArgs_success_null() throws Exception {
		ServiceDispatcherImpl underTest = new ServiceDispatcherImpl(Collections.emptyMap(), serializer, Collections.emptyMap());

		HashMap<String, Integer> data = new HashMap<>();
		data.put("Cd", Integer.valueOf(25));
		data.put("Dc", Integer.valueOf(31));
		LinkedHashMap<String, Serializable> args = new LinkedHashMap<>();
		args.put("data", data);

		Object[] actual = underTest.deserialize(context, method, serializer.serialize(args).get()).get();
		assertEquals(3, actual.length);
		assertSame(context, actual[0]);
		assertNull(actual[1]);
		assertEquals(data, actual[2]);
	}

	@Test
	public void deserialize_extraArgs_throws() throws Exception {
		ServiceDispatcherImpl underTest = new ServiceDispatcherImpl(Collections.emptyMap(), serializer, Collections.emptyMap());

		HashSet<String> keys = new HashSet<>(Arrays.asList("Ab", "Bc"));
		LinkedHashMap<String, Serializable> args = new LinkedHashMap<>();
		args.put("keys", keys);

		Method emptyMethod = AService.class.getMethod("empty", Context.class);
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: Too many arguments (3 instead of 1) to AService.empty");
		underTest.deserialize(context, emptyMethod, serializer.serialize(args).get()).get();
	}
}
