/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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


public abstract class AbstractInvocationTestsuite {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	static SyncService syncService;

	static AsyncService asyncService;

	static ThrowingService throwingService;

	private final int nthreads = 5;

	private final int nrequests = 200;

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
	public void roundtrip_exceptional_success() {
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
