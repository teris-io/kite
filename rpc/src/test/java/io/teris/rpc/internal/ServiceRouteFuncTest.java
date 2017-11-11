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

import io.teris.rpc.ExportName;
import io.teris.rpc.ExportPath;


public class ServiceRouteFuncTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@ExportPath(replace = "io.teris.rpc.internal.ServiceRouteFuncTest", value = "")
	interface Service {

		@ExportName("")
		void emptyFullRoute();

		void emptyServiceRoute();
	}

	interface NestedService {
		void foo();
	}

	@ExportPath(value = "some.path")
	interface PathOvewriteService {
		void foo();
	}

	@ExportPath(replace = "internal.ServiceRouteFuncTest", value = "some.path")
	interface PartSubstituteService {
		void foo();
	}

	@ExportName("thing")
	@ExportPath(replace = "internal.ServiceRouteFuncTest", value = "some.path")
	interface NoThingService {
		@ExportName("foo")
		void nofoo();
	}

	@Test
	public void route_empty_throws() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		Service s = Proxier.get(Service.class, done);
		s.emptyFullRoute();
		exception.expect(ExecutionException.class);
		exception.expectMessage("Empty root for Service.emptyFullRoute");
		done.get();
	}

	@Test
	public void route_emptyPath_nonEmptyMethod_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		Service s = Proxier.get(Service.class, done);
		s.emptyServiceRoute();
		assertEquals("emptyserviceroute", done.get());
	}

	@Test
	public void route_standard_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		ServiceRouteFuncTestService s = Proxier.get(ServiceRouteFuncTestService.class, done);
		s.foo();
		assertEquals("io.teris.rpc.internal.serviceroutefunctest.foo", done.get());
	}

	@Test
	public void route_nested_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		NestedService s = Proxier.get(NestedService.class, done);
		s.foo();
		assertEquals("io.teris.rpc.internal.serviceroutefunctest.nested.foo", done.get());
	}

	@Test
	public void route_pathOverwrite_success() throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		PathOvewriteService s = Proxier.get(PathOvewriteService.class, done);
		s.foo();
		assertEquals("some.path.pathovewrite.foo", done.get());
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
		NoThingService s = Proxier.get(NoThingService.class, done);
		s.nofoo();
		assertEquals("io.teris.rpc.some.path.thing.foo", done.get());
	}

	private static class Proxier implements InvocationHandler {

		private final CompletableFuture<String> done;

		private final ServiceRouteFunc serviceRouteFunc = new ServiceRouteFunc();

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
				done.complete(serviceRouteFunc.apply(method));
			}
			catch (Exception ex) {
				done.completeExceptionally(ex);
			}
			return null;
		}
	}
}
