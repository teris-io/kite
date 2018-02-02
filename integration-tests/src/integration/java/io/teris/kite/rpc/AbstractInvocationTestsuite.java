/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.kite.Context;
import io.teris.kite.Serializer;
import io.teris.kite.gson.JsonSerializer;
import io.teris.kite.rpc.impl.AsyncServiceImpl;
import io.teris.kite.rpc.impl.SyncServiceImpl;
import io.teris.kite.rpc.impl.ThrowingServiceImpl;


public abstract class AbstractInvocationTestsuite {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	static int port;

	static ServiceExporter exporter1;

	static ServiceExporter exporter2;

	static SyncService syncService;

	static AsyncService asyncService;

	static ThrowingService throwingService;

	private final int nthreads = 5;

	private final int nrequests = 200;

	static void preInit() {
		while (true) {
			try (ServerSocket socket = new ServerSocket((int) (49152 + Math.random() * (65535 - 49152)))) {
				port = socket.getLocalPort();
				break;
			}
			catch (IOException e) {
				// repeat
			}
		}

		Serializer serializer = JsonSerializer.builder().build();

		exporter1 = ServiceExporter.serializer(serializer)
			.preprocessor((context, data) -> {
				CompletableFuture<Context> res = new CompletableFuture<>();
				if (context.containsKey("x-technical-error")) {
					res.completeExceptionally(new RuntimeException("BOOM"));
				}
				else if (context.containsKey("x-auth-error")) {
					res.completeExceptionally(new AuthenticationException("BOOM"));
				}
				else {
					res.complete(context);
				}
				return res;
			})
			.export(SyncService.class, new SyncServiceImpl("1"))
			.export(AsyncService.class, new AsyncServiceImpl("2"))
			.build();

		exporter2 = ServiceExporter.serializer(serializer)
			.export(ThrowingService.class, new ThrowingServiceImpl("3"))
			.build();
	}


	@Test
	public void roundtrip_single_sync_success() {
		Context context = new Context();
		Double res = syncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		assertEquals(700.5, res.doubleValue(), 0.001);
		assertEquals("1", context.get("invoked-by"));
	}

	@Test
	public void roundtrip_dual_sync_success() {
		Context context = new Context();
		Double res = syncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		assertEquals(700.5, res.doubleValue(), 0.001);
		assertEquals("1", context.get("invoked-by"));
		res = syncService.minus(context, Double.valueOf(359.3), Double.valueOf(341.2));
		assertEquals(18.1, res.doubleValue(), 0.001);
		assertEquals("1", context.get("invoked-by"));
	}

	@Test
	public void roundtrip_signle_async_success() throws Exception {
		Context context = new Context();
		CompletableFuture<Double> plusPromise = asyncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		assertEquals(700.5, plusPromise.get().doubleValue(), 0.001);
		assertEquals("2", context.get("invoked-by"));
	}

	@Test
	public void roundtrip_dual_async_success() throws Exception {
		Context context = new Context();
		CompletableFuture<Double> plusPromise = asyncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		CompletableFuture<Double> minusPromise = asyncService.minus(context, Double.valueOf(359.3), Double.valueOf(341.2));
		assertEquals(700.5, plusPromise.get().doubleValue(), 0.001);
		assertEquals("2", context.get("invoked-by"));
		assertEquals(18.1, minusPromise.get().doubleValue(), 0.001);
		assertEquals("2", context.get("invoked-by"));
	}

	@Test
	public void roundtrip_businessException() {
		Context context = new Context();
		CompletableFuture<Void> boomPromise = throwingService.boomThen(context);
		try {
			throwingService.boomNow(context);
			throw new AssertionError("unreachable code");
		}
		catch (BusinessException ex) {
			assertEquals("3", context.get("invoked-by"));
			assertTrue(ex.getMessage().contains("IllegalStateException: BOOM"));
		}
		try {
			boomPromise.get();
			throw new AssertionError("unreachable code");
		}
		catch (ExecutionException | InterruptedException ex) {
			assertEquals("3", context.get("invoked-by"));
			assertTrue(ex.getMessage().contains("BusinessException: IllegalStateException: BOOM"));
		}
	}

	@Test
	public void roundtrip_technicalException() {
		Context context = new Context();
		context.put("x-technical-error", "yes");
		try {
			syncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
			throw new AssertionError("unreachable code");
		}
		catch (TechnicalException ex) {
			assertTrue(ex.getMessage().contains("BOOM"));
		}
		CompletableFuture<Double> promise = asyncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		try {
			promise.get();
			throw new AssertionError("unreachable code");
		}
		catch (ExecutionException | InterruptedException ex) {
			assertTrue(ex.getMessage().contains("TechnicalException: BOOM"));
		}
	}

	@Test
	public void roundtrip_authenticationException() {
		Context context = new Context();
		context.put("x-auth-error", "yes");
		try {
			syncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
			throw new AssertionError("unreachable code");
		}
		catch (AuthenticationException ex) {
			assertEquals("BOOM", ex.getMessage());
		}
		CompletableFuture<Double> promise = asyncService.plus(context, Double.valueOf(341.2), Double.valueOf(359.3));
		try {
			promise.get();
			throw new AssertionError("unreachable code");
		}
		catch (ExecutionException | InterruptedException ex) {
			assertTrue(ex.getCause() instanceof AuthenticationException);
			assertEquals("BOOM", ex.getCause().getMessage());
		}
	}

	@Test
	public void benchmark_async_invocationsNThreadsxMRequests() throws Exception {
		List<Callable<Void>> callables = new ArrayList<>();
		for (int i = 0; i < nthreads; i++) {
			callables.add(() -> {
				List<Future<?>> invocations = new ArrayList<>();
				for (int j = 0; j < nrequests; j++) {
					invocations.add(asyncService.plus(new Context(), Double.valueOf(341.2), Double.valueOf(359.3)));
				}
				for (Future<?> future: invocations) {
					future.get();
				}
				return null;
			});
		}
		ExecutorService pool = Executors.newFixedThreadPool(nthreads);
		for (Future<Void> future: pool.invokeAll(callables)) {
			future.get();
		}
	}

	@Test
	public void benchmark_sync_invocationsNThreadsxMRequests() throws Exception {
		List<Callable<Void>> callables = new ArrayList<>();
		for (int i = 0; i < nthreads; i++) {
			callables.add(() -> {
				for (int j = 0; j < nrequests; j++) {
					syncService.plus(new Context(), Double.valueOf(341.2), Double.valueOf(359.3));
				}
				return null;
			});
		}
		ExecutorService pool = Executors.newFixedThreadPool(nthreads);
		for (Future<Void> future: pool.invokeAll(callables)) {
			future.get();
		}
	}
}
