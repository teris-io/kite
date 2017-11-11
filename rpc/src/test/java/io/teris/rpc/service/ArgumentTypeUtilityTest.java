/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.service;

import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;


public class ArgumentTypeUtilityTest {

	interface ArgsVariationService {

		void noargs();

		void varargsOnly(Integer... ints);

		void varargsEmpty(Integer... ints);

		void withVarargs(String title, String key, Integer... values);

		void ignoreVarargs(String title, String key, Integer... values);

		void nonSerializable(String title, Object key);

		void nonSerializableVararg(String key, Object... values);

		void generics(ArrayList<HashMap<String, Double>> map, HashSet<Integer> set);

		void wildcard(ArrayList<?> list);

		void genericsNonSerializableParam(ArrayList<Object> list);

		void genericsNonSerializable(List<String> list);
	}


	@Test
	public void arguments_noargs_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.noargs();
		assertEquals(0, done.get().size());
	}

	@Test
	public void arguments_varargsOnly_success() throws Exception {
		CompletableFuture<LinkedHashMap<String, Serializable>> done = new CompletableFuture<>();
		ArgsVariationService s = Proxier.get(ArgsVariationService.class, done);
		s.varargsOnly(Integer.valueOf(25), Integer.valueOf(36));
		LinkedHashMap<String, Serializable> actual = done.get();
		assertEquals(1, actual.size());
		assertEquals(new Integer[]{Integer.valueOf(25), Integer.valueOf(36)}, actual.get("ints"));
	}

	private static class Proxier<RS> implements InvocationHandler {

		private final CompletableFuture<LinkedHashMap<String, Serializable>> done;

		private final ArgumentTypeUility argumentTypeUility = new ArgumentTypeUility();

		Proxier(CompletableFuture<LinkedHashMap<String, Serializable>> done) {
			this.done = done;
		}

		static <S, RS> S get(Class<S> serviceClass, CompletableFuture<LinkedHashMap<String, Serializable>> done) {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(Proxier.class.getClassLoader(), new Class[]{ serviceClass }, new Proxier<>(done));
			return res;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			try {
				done.complete(argumentTypeUility.extractArguments(method, args));
			}
			catch (Exception ex) {
				done.completeExceptionally(ex);
			}
			return null;
		}
	}

}
