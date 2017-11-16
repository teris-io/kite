/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Name;
import io.teris.rpc.Service;


public class ProxyMethodUtilRouteTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Service(replace = "io.teris.rpc.internal.ProxyMethodUtilRouteTest.A")
	interface AService {

		@Name("")
		void emptyFullRoute();

		void emptyServiceRoute();
	}

	@Service
	interface NestedService {
		void foo();
	}

	@Service("some.path")
	interface PathOvewriteService {
		void foo();
	}

	@Service(replace = "internal.ProxyMethodUtilRouteTest", value = "some.path")
	interface PartSubstituteService {
		void foo();
	}

	@Service(replace = "internal.ProxyMethodUtilRouteTest", value = "some.path")
	interface ThingService {
		@Name("foo")
		void nofoo();
	}

	@Test
	public void route_empty_throws() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		AService s = Proxier.get(AService.class, done);
		s.emptyFullRoute();
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: Empty route for AService.emptyFullRoute");
		done.get();
	}

	@Test
	public void route_emptyPath_nonEmptyMethod_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		AService s = Proxier.get(AService.class, done);
		s.emptyServiceRoute();
		assertEquals("emptyserviceroute", done.get());
	}

	@Test
	public void route_nested_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		NestedService s = Proxier.get(NestedService.class, done);
		s.foo();
		assertEquals("io.teris.rpc.internal.proxymethodutilroutetest.nested.foo", done.get());
	}

	@Test
	public void route_pathOverwrite_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		PathOvewriteService s = Proxier.get(PathOvewriteService.class, done);
		s.foo();
		assertEquals("some.path.foo", done.get());
	}

	@Test
	public void route_partSubstitute_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		PartSubstituteService s = Proxier.get(PartSubstituteService.class, done);
		s.foo();
		assertEquals("io.teris.rpc.some.path.partsubstitute.foo", done.get());
	}

	@Test
	public void route_allSubstitute_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		ThingService s = Proxier.get(ThingService.class, done);
		s.nofoo();
		assertEquals("io.teris.rpc.some.path.thing.foo", done.get());
	}

	private static class Proxier implements InvocationHandler {

		private final CompletableFuture<String> done;

		Proxier(CompletableFuture<String> done) {
			this.done = done;
		}

		static <S> S get(Class<S> serviceClass, CompletableFuture<String> done) {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(Proxier.class.getClassLoader(), new Class[]{ serviceClass }, new Proxier(done));
			return res;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			try {
				done.complete(ProxyMethodUtil.route(method));
			}
			catch (Exception ex) {
				done.completeExceptionally(ex);
			}
			return null;
		}
	}
}
