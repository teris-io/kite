/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.rpc.Name;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;


public class ProxyMethodUtilArgumentsTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();


	interface FailingArgs {

		void unannotated(@Name("title") String title, Integer unannotated);

		void wildcard(@Name("list") ArrayList<?> list);

		void nonSerializable(@Name("title") Object title);

		void nonSerializableVararg(@Name("key") String key, @Name("values") Object... values);

		void genericsNonSerializable(@Name("list") List<String> list);

		void genericsNonSerializableParam(@Name("list") ArrayList<Object> list);

		void emptyName(@Name("") String title);
	}

	interface ArgsVariationService {

		void noargs();

		void varargsOnly(@Name("ints") Integer... ints);

		void withVarargs(@Name("title") String title, @Name("key") String key, @Name("ints") Integer... values);

		void generics(@Name("map") ArrayList<HashMap<String, Double>> map, @Name("set") HashSet<Integer> set);
	}

	@Test
	public void arguments_unannotated_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.unannotated("a", Integer.valueOf(25));
		exception.expect(ExecutionException.class);
		exception.expectMessage("Arguments of the service method 'unannotated' must be annotated with Name");
		done.get();
	}

	@Test
	public void arguments_wildcards_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.wildcard(new ArrayList<>());
		exception.expect(ExecutionException.class);
		exception.expectMessage("Argument types of the service method 'wildcard' must contain no wildcards");
		done.get();
	}

	@Test
	public void arguments_nonSerializable_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.nonSerializable("a");
		exception.expect(ExecutionException.class);
		exception.expectMessage("Argument types of the service method 'nonSerializable' must implement Serializable or be void");
		done.get();
	}

	@Ignore("FIXME unsure how to get and check type of elements for arrays")
	@Test
	public void arguments_nonSerializableVararg_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.nonSerializableVararg("a", "b", "c");
		exception.expect(ExecutionException.class);
		exception.expectMessage("Argument types of the service method 'nonSerializableVararg' must implement Serializable or be void");
		done.get();
	}

	@Test
	public void arguments_genericsNonSerializable_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.genericsNonSerializable(new ArrayList<>());
		exception.expect(ExecutionException.class);
		exception.expectMessage("Argument types of the service method 'genericsNonSerializable' must implement Serializable or be void");
		done.get();
	}

	@Test
	public void arguments_genericsNonSerializableParam_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.genericsNonSerializableParam(new ArrayList<>());
		exception.expect(ExecutionException.class);
		exception.expectMessage("Argument types of the service method 'genericsNonSerializableParam' must implement Serializable or be void");
		done.get();
	}

	@Test
	public void arguments_emptyName_throws() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.emptyName("");
		exception.expect(ExecutionException.class);
		exception.expectMessage("Service method argument names must not be empty");
		done.get();
	}

	@Test
	public void arguments_noargs_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.noargs();
		assertNull(done.get());
	}

	@Test
	public void arguments_varargsOnly_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.varargsOnly(Integer.valueOf(25), Integer.valueOf(36));
		LinkedHashMap<String, Serializable> actual = done.get();
		assertEquals(1, actual.size());
		assertTrue(Arrays.equals(new Integer[]{Integer.valueOf(25), Integer.valueOf(36)}, (Integer[]) actual.get("ints")));
	}

	@Test
	public void arguments_varargsEmpty_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.varargsOnly();
		LinkedHashMap<String, Serializable> actual = done.get();
		assertEquals(1, actual.size());
		assertTrue(Arrays.equals(new Integer[]{},  (Integer[]) actual.get("ints")));
	}

	@Test
	public void arguments_withVarargs_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.withVarargs("TestTitle", "TestKey", Integer.valueOf(25), Integer.valueOf(36));
		LinkedHashMap<String, Serializable> actual = done.get();
		assertEquals(3, actual.size());
		Iterator<String> keyIter = actual.keySet().iterator();
		String key = keyIter.next();
		assertEquals("title", key);
		assertEquals("TestTitle", actual.get(key));
		key = keyIter.next();
		assertEquals("key", key);
		assertEquals("TestKey", actual.get(key));
		key = keyIter.next();
		assertEquals("ints", key);
		assertTrue(Arrays.equals(new Integer[]{Integer.valueOf(25), Integer.valueOf(36)}, (Integer[]) actual.get(key)));
	}

	@Test
	public void arguments_generics_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		ArrayList<HashMap<String, Double>> map = new ArrayList<>();
		map.add(new HashMap<>());
		map.get(0).put("a", Double.valueOf(25.1));
		HashSet<Integer> set = new HashSet<>();
		set.add(Integer.valueOf(31));
		s.generics(map, set);
		LinkedHashMap<String, Serializable> actual = done.get();
		assertSame(map, actual.get("map"));
		assertSame(set, actual.get("set"));
	}

	private static class Proxier implements InvocationHandler {

		private final CompletableFuture<LinkedHashMap<String, Serializable>> done;

		Proxier(CompletableFuture<LinkedHashMap<String, Serializable>> done) {
			this.done = done;
		}

		static <S> S get(Class<S> serviceClass, CompletableFuture<LinkedHashMap<String, Serializable>> done) {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(Proxier.class.getClassLoader(), new Class[]{ serviceClass }, new Proxier(done));
			return res;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			try {
				done.complete(ProxyMethodUtil.arguments(method, args));
			}
			catch (Exception ex) {
				done.completeExceptionally(ex);
			}
			return null;
		}
	}

}
