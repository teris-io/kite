/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.ServiceException;


public class ProxyMethodUtilReturnTypeTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();


	interface NonGenericsReturnValueService {

		void voidable1();

		Void voidable2();

		CompletableFuture<Void> voidableAsync();

		Integer serializable();

		CompletableFuture<Integer> serializableAsync();

		Object nonSerializable();

		CompletableFuture<Object> nonSerializableAsync();

		Object[] nonSerializableArray();

		Integer[] serializableArray();
	}

	@Test
	public void return_void_success() {
		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, null);
		s.voidable1();
	}

	@Test
	public void return_Void_success() {
		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, null);
		s.voidable2();
	}

	@Test
	public void return_void_async_success() throws Exception {
		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, null);
		s.voidableAsync().get();
	}

	@Test
	public void return_serializable_success() {
		Integer data = Integer.valueOf(25);
		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, data);
		assertEquals(25, s.serializable().intValue());
	}

	@Test
	public void return_serializable_async_success() throws Exception {
		Integer data = Integer.valueOf(25);
		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, data);
		assertEquals(25, s.serializableAsync().get().intValue());
	}

	@Test
	public void return_nonSerializable_throws() {
		Object data = Void.class;

		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, data);
		exception.expect(RuntimeException.class);
		exception.expectMessage("Failed to invoke NonGenericsReturnValueService.nonSerializable: return type must implement Serializable or be void/Void");
		s.nonSerializable();
	}

	@Test
	public void return_nonSerializable_async_throws() throws Exception {
		Object data = Void.class;

		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, data);
		exception.expect(ExecutionException.class);
		exception.expectMessage("Failed to invoke NonGenericsReturnValueService.nonSerializableAsync: return type must implement Serializable or be void/Void");
		s.nonSerializableAsync().get();
	}

	@Test
	public void return_nonSerializable_array_throws() throws Exception {
		Object data = Void.class;

		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, data);
		exception.expect(RuntimeException.class);
		exception.expectMessage("Failed to invoke NonGenericsReturnValueService.nonSerializableArray: return type must implement Serializable or be void/Void");
		s.nonSerializableArray();
	}

	@Test
	public void return_serializable_array_success() throws Exception {
		Integer[] data = new Integer[]{ Integer.valueOf(25) };
		NonGenericsReturnValueService s = Proxier.get(NonGenericsReturnValueService.class, data);
		assertTrue(Arrays.equals(new Integer[]{ Integer.valueOf(25) }, s.serializableArray()));
	}

	interface GenericsReturnValueService {

		ArrayList<Integer> arrayList();

		CompletableFuture<ArrayList<Integer>> arrayListAsync();

		HashSet<Integer> hashSet();

		CompletableFuture<HashSet<Integer>> hashSetAsync();

		TreeSet<Integer> treeSet();

		CompletableFuture<TreeSet<Integer>> treeSetAsync();

		HashMap<Integer, String> hashMap();

		CompletableFuture<HashMap<Integer, String>> hashMapAsync();

		TreeMap<Integer, String> treeMap();

		CompletableFuture<TreeMap<Integer, String>> treeMapAsync();

		List<Integer> genericIface();

		CompletableFuture<List<Integer>> genericIfaceAsync();

		ArrayList<Object> objectCollection();

		CompletableFuture<ArrayList<Object>> objectCollectionAsync();

		ArrayList<?> wildcard();

		CompletableFuture<ArrayList<?>> wildcardAsync();
	}

	@Test
	public void return_arrayList_success() {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		List<Integer> res = s.arrayList();
		assertEquals(2, res.size());
		assertEquals(25, res.get(0).intValue());
		assertEquals(36, res.get(1).intValue());
	}

	@Test
	public void return_arrayList_async_success() throws Exception {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		List<Integer> res = s.arrayListAsync().get();
		assertEquals(2, res.size());
		assertEquals(25, res.get(0).intValue());
		assertEquals(36, res.get(1).intValue());
	}

	@Test
	public void return_hashSet_success() {
		HashSet<Integer> data = new HashSet<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Set<Integer> res = s.hashSet();
		assertEquals(2, res.size());
		assertTrue(res.contains(Integer.valueOf(25)));
		assertTrue(res.contains(Integer.valueOf(36)));
	}

	@Test
	public void return_hashSet_async_success() throws Exception {
		HashSet<Integer> data = new HashSet<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Set<Integer> res = s.hashSetAsync().get();
		assertEquals(2, res.size());
		assertTrue(res.contains(Integer.valueOf(25)));
		assertTrue(res.contains(Integer.valueOf(36)));
	}

	@Test
	public void return_treeSet_success() {
		TreeSet<Integer> data = new TreeSet<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));
		data.add(Integer.valueOf(-51));
		data.add(Integer.valueOf(31));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Set<Integer> res = s.treeSet();
		assertEquals("[-51, 25, 31, 36]", res.toString());
	}

	@Test
	public void return_treeSet_async_success() throws Exception {
		TreeSet<Integer> data = new TreeSet<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));
		data.add(Integer.valueOf(-51));
		data.add(Integer.valueOf(31));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Set<Integer> res = s.treeSetAsync().get();
		assertEquals("[-51, 25, 31, 36]", res.toString());
	}

	@Test
	public void return_hashMap_success() {
		HashMap<Integer, String> data = new HashMap<>();
		data.put(Integer.valueOf(25), "value1");
		data.put(Integer.valueOf(36), "value2");

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Map<Integer, String> res = s.hashMap();
		assertEquals(2, res.size());
		assertEquals("value1", res.get(Integer.valueOf(25)));
		assertEquals("value2", res.get(Integer.valueOf(36)));
	}

	@Test
	public void return_hashMap_async_success() throws Exception {
		HashMap<Integer, String> data = new HashMap<>();
		data.put(Integer.valueOf(25), "value1");
		data.put(Integer.valueOf(36), "value2");

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Map<Integer, String> res = s.hashMapAsync().get();
		assertEquals(2, res.size());
		assertEquals("value1", res.get(Integer.valueOf(25)));
		assertEquals("value2", res.get(Integer.valueOf(36)));
	}

	@Test
	public void return_treeMap_success() {
		TreeMap<Integer, String> data = new TreeMap<>();
		data.put(Integer.valueOf(25), "value1");
		data.put(Integer.valueOf(36), "value2");
		data.put(Integer.valueOf(-151), "value3");
		data.put(Integer.valueOf(18), "value4");

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Map<Integer, String> res = s.treeMap();
		assertEquals("{-151=value3, 18=value4, 25=value1, 36=value2}", res.toString());
	}

	@Test
	public void return_treeMap_async_success() throws Exception {
		TreeMap<Integer, String> data = new TreeMap<>();
		data.put(Integer.valueOf(25), "value1");
		data.put(Integer.valueOf(36), "value2");
		data.put(Integer.valueOf(-151), "value3");
		data.put(Integer.valueOf(18), "value4");

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		Map<Integer, String> res = s.treeMapAsync().get();
		assertEquals("{-151=value3, 18=value4, 25=value1, 36=value2}", res.toString());
	}

	@Test
	public void return_genericIface_throws() {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		exception.expect(RuntimeException.class);
		exception.expectMessage("Failed to invoke GenericsReturnValueService.genericIface: return type must implement Serializable or be void/Void");
		s.genericIface();
	}

	@Test
	public void return_genericIface_async_throws() throws Exception {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		exception.expect(ExecutionException.class);
		exception.expectMessage("Failed to invoke GenericsReturnValueService.genericIfaceAsync: return type must implement Serializable or be void/Void");
		s.genericIfaceAsync().get();
	}

	@Test
	public void return_objectCollection_throws() {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		exception.expect(RuntimeException.class);
		exception.expectMessage("Failed to invoke GenericsReturnValueService.objectCollection: return type must implement Serializable or be void/Void");
		s.objectCollection();
	}

	@Test
	public void return_objectCollection_async_throws() throws Exception {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		exception.expect(ExecutionException.class);
		exception.expectMessage("Failed to invoke GenericsReturnValueService.objectCollectionAsync: return type must implement Serializable or be void/Void");
		s.objectCollectionAsync().get();
	}

	@Test
	public void return_wildcard_throws() {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		exception.expect(RuntimeException.class);
		exception.expectMessage("Failed to invoke GenericsReturnValueService.wildcard: return type must contain no wildcards");
		s.wildcard();
	}

	@Test
	public void return_wildcard_async_throws() throws Exception {
		ArrayList<Integer> data = new ArrayList<>();
		data.add(Integer.valueOf(25));
		data.add(Integer.valueOf(36));

		GenericsReturnValueService s = Proxier.get(GenericsReturnValueService.class, data);
		exception.expect(ExecutionException.class);
		exception.expectMessage("Failed to invoke GenericsReturnValueService.wildcardAsync: return type must contain no wildcards");
		s.wildcardAsync().get();
	}

	private static class Proxier<RS> implements InvocationHandler {

		private final RS response;

		Proxier(RS response) {
			this.response = response;
		}

		static <S, RS> S get(Class<S> serviceClass, RS response) {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(Proxier.class.getClassLoader(), new Class[]{ serviceClass }, new Proxier<>(response));
			return res;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			CompletableFuture<RS> promise = doInvoke(method);
			if (Future.class.isAssignableFrom(method.getReturnType())) {
				return promise;
			}
			try {
				return promise.get();
			}
			catch (ExecutionException ex) {
				throw ex.getCause();
			}
		}

		private CompletableFuture<RS> doInvoke(Method method) {
			CompletableFuture<RS> res = new CompletableFuture<>();
			try {
				ProxyMethodUtil.returnType(method);
				res.complete(response);
			}
			catch (RuntimeException ex) {
				res.completeExceptionally(ex);
			}
			catch (ServiceException ex) {
				res.completeExceptionally(new RuntimeException(ex));
			}
			return res;
		}
	}
}
