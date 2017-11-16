/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import static org.junit.Assert.assertEquals;
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
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Context;
import io.teris.rpc.Name;


public class ProxyMethodUtilArgumentsTest {

	private static final Context context = new Context();

	@Rule
	public ExpectedException exception = ExpectedException.none();


	interface FailingArgs {

		void noargs();

		void noContext(@Name("title") String title);

		void emptyContext(Context context);

		void unannotated(Context context, @Name("title") String title, Integer unannotated);

		void wildcard(Context context, @Name("list") ArrayList<?> list);

		void nonSerializable(Context context, @Name("title") Object title);

		void nonSerializableVararg(Context context, @Name("key") String key, @Name("values") Object... values);

		void genericsNonSerializable(Context context, @Name("list") List<String> list);

		void genericsNonSerializableParam(Context context, @Name("list") ArrayList<Object> list);

		void emptyName(Context context, @Name("") String title);
	}

	interface ArgsVariationService {

		void contextOnly(Context context);

		void contextReturned(Context context, @Name("value") Integer value);

		void varargsOnly(Context context, @Name("ints") Integer... ints);

		void withVarargs(Context context, @Name("title") String title, @Name("key") String key, @Name("ints") Integer... values);

		void generics(Context context, @Name("map") ArrayList<HashMap<String, Double>> map, @Name("set") HashSet<Integer> set);
	}

	@Test
	public void arguments_noDeclaredContext_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.noContext("title");
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: First parameters to FailingArgs.noContext must be Context");
		done.get();
	}

	@Test
	public void arguments_missingContext_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.emptyContext(null);
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: First argument to FailingArgs.emptyContext must be a (non-null) instance of Context");
		done.get();
	}

	@Test
	public void arguments_noargs_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.noargs();
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: First parameters to FailingArgs.noargs must be Context");
		done.get();
	}

	@Test
	public void arguments_unannotated_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.unannotated(context,"a", Integer.valueOf(25));
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: After Context all parameters in FailingArgs.unannotated must be annotated with @Name");
		done.get();
	}

	@Test
	public void arguments_wildcards_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.wildcard(context, new ArrayList<>());
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: Parameter types in FailingArgs.wildcard must contain no wildcards");
		done.get();
	}

	@Test
	public void arguments_nonSerializable_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.nonSerializable(context, "a");
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: After Context all parameter types in FailingArgs.nonSerializable must implement Serializable");
		done.get();
	}

	@Test
	public void arguments_nonSerializableVararg_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.nonSerializableVararg(context, "a", "b", "c");
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: After Context all parameter types in FailingArgs.nonSerializableVararg must implement Serializable");
		done.get();
	}

	@Test
	public void arguments_genericsNonSerializable_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.genericsNonSerializable(context, new ArrayList<>());
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: After Context all parameter types in FailingArgs.genericsNonSerializable must implement Serializable");
		done.get();
	}

	@Test
	public void arguments_genericsNonSerializableParam_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.genericsNonSerializableParam(context, new ArrayList<>());
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: After Context all parameter types in FailingArgs.genericsNonSerializableParam must implement Serializable");
		done.get();
	}

	@Test
	public void arguments_emptyName_throws() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		FailingArgs s = Proxier.get(FailingArgs.class, done);
		s.emptyName(context, "");
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: Empty @Name annotation in FailingArgs.emptyName");
		done.get();
	}

	@Test
	public void arguments_contextOnly_success() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.contextOnly(context);
		Context actual = done.get().getKey();
		assertSame(context, actual);
	}

	@Test
	public void arguments_contextReturned_success() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.contextReturned(context, Integer.valueOf(25));
		Context actual = done.get().getKey();
		assertSame(context, actual);
	}

	@Test
	public void arguments_varargsOnly_success() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.varargsOnly(context, Integer.valueOf(25), Integer.valueOf(36));
		LinkedHashMap<String, Serializable> actual = done.get().getValue();
		assertEquals(1, actual.size());
		assertTrue(Arrays.equals(new Integer[]{Integer.valueOf(25), Integer.valueOf(36)}, (Integer[]) actual.get("ints")));
	}

	@Test
	public void arguments_varargsEmpty_success() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.varargsOnly(context);
		LinkedHashMap<String, Serializable> actual = done.get().getValue();
		assertEquals(1, actual.size());
		assertTrue(Arrays.equals(new Integer[]{},  (Integer[]) actual.get("ints")));
	}

	@Test
	public void arguments_withVarargs_success() throws Exception {
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.withVarargs(context, "TestTitle", "TestKey", Integer.valueOf(25), Integer.valueOf(36));
		LinkedHashMap<String, Serializable> actual = done.get().getValue();
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
		CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		ArrayList<HashMap<String, Double>> map = new ArrayList<>();
		map.add(new HashMap<>());
		map.get(0).put("a", Double.valueOf(25.1));
		HashSet<Integer> set = new HashSet<>();
		set.add(Integer.valueOf(31));
		s.generics(context, map, set);
		LinkedHashMap<String, Serializable> actual = done.get().getValue();
		assertSame(map, actual.get("map"));
		assertSame(set, actual.get("set"));
	}

	private static class Proxier implements InvocationHandler {

		private final CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done;

		Proxier(CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done) {
			this.done = done;
		}

		static <S> S get(Class<S> serviceClass, CompletableFuture<Entry<Context, LinkedHashMap<String, Serializable>>> done) {
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
