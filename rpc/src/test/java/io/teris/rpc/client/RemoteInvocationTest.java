/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Deserializer;
import io.teris.rpc.Serializer;
import io.teris.rpc.Transporter;
import io.teris.rpc.testfixture.JsonDeserializer;
import io.teris.rpc.testfixture.JsonSerializer;


public class RemoteInvocationTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private static final Serializer serializer = new JsonSerializer();

	private static final Map<String, Deserializer> deserializerMap = new HashMap<>();

	static {
		deserializerMap.put(serializer.getContentType(), new JsonDeserializer());
	}

	interface VoidService {

		void voidable();

		CompletableFuture<Void> voidableAsync();
	}

	@Test
	public void voidSyncReturn_nullTransfer_success() {
		Transporter transporter = getTransporterMock(null);
		RemoteInvocation handler = new RemoteInvocation(serializer, transporter, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		s.voidable();
		verify(transporter).transport(any(), any(), any());
	}

	@Test
	public void voidSyncReturn_nonNullTransfer_throws() {
		// looks like accepted value for void (so it should actually fail trying to find deserializer)
		Transporter transporter = getTransporterMock("true".getBytes());
		RemoteInvocation handler = new RemoteInvocation(serializer, transporter, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		exception.expect(RuntimeException.class);
		exception.expectMessage("Internal error: can't find deserializer for void");
		s.voidable();
	}

	@Test
	public void voidAsyncReturn_nullTransfer_success() throws Exception {
		Transporter transporter = getTransporterMock(null);
		RemoteInvocation handler = new RemoteInvocation(serializer, transporter, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		s.voidableAsync().get();
		verify(transporter).transport(any(), any(), any());
	}

	@Test
	public void voidAsyncReturn_nonNullTransfer_throws() throws Exception {
		// looks like accepted value for void (so it should actually fail trying to find deserializer)
		Transporter transporter = getTransporterMock("true".getBytes());
		RemoteInvocation handler = new RemoteInvocation(serializer, transporter, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		CompletableFuture<Void> future = s.voidableAsync();
		exception.expect(ExecutionException.class);
		exception.expectMessage("Cannot construct instance of `java.lang.Void`");
		future.get();
	}

	private <S> S getProxy(Class<S> serviceClass, RemoteInvocation handler) {
		@SuppressWarnings("unchecked")
		S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass }, handler);
		return res;
	}

	private Transporter getTransporterMock(byte[] res) {
		CompletableFuture<byte[]> tf = new CompletableFuture<>();
		tf.complete(res);

		Transporter transporter = mock(Transporter.class);
		doReturn(tf).when(transporter).transport(any(), any(), any());
		return transporter;
	}
}
