/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Proxy;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.ServiceCreatorImpl.ClientServiceInvocationHandler;
import io.teris.rpc.testfixture.TestDeserializer;
import io.teris.rpc.testfixture.TestSerializer;


public class ServiceCreatorImplTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private static final Serializer serializer = new TestSerializer();

	private static final Map<String, Deserializer> deserializerMap = new HashMap<>();

	static {
		deserializerMap.put(serializer.contentType(), new TestDeserializer());
	}

	@Service
	interface VoidService {

		void voidable(Context context);

		CompletableFuture<Void> voidableAsync(Context context);
	}

	@Test
	public void voidSyncReturn_nullTransfer_success() {
		ServiceInvoker requester = remoteCallerMock(null);
		ClientServiceInvocationHandler handler = new ClientServiceInvocationHandler(requester, serializer, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		s.voidable(new Context());
		verify(requester).call(any(), any(), any());
	}

	@Test
	public void voidSyncReturn_nonNullTransfer_throws() {
		// looks like accepted value for void (so it should actually fail trying to find deserializer)
		ServiceInvoker requester = remoteCallerMock("{\"payload\": {}}".getBytes());
		ClientServiceInvocationHandler handler = new ClientServiceInvocationHandler(requester, serializer, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		exception.expect(RuntimeException.class);
		exception.expectMessage("GSON cannot handle void");
		s.voidable(new Context());
	}

	@Test
	public void voidAsyncReturn_nullTransfer_success() throws Exception {
		ServiceInvoker requester = remoteCallerMock(null);
		ClientServiceInvocationHandler handler = new ClientServiceInvocationHandler(requester, serializer, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		s.voidableAsync(new Context()).get();
		verify(requester).call(any(), any(), any());
	}

	@Test
	public void voidAsyncReturn_nonNullTransfer_throws() throws Exception {
		ServiceInvoker requester = remoteCallerMock("{\"payload\": {}}".getBytes());
		ClientServiceInvocationHandler handler = new ClientServiceInvocationHandler(requester, serializer, deserializerMap);
		VoidService s = getProxy(VoidService.class, handler);
		CompletableFuture<Void> future = s.voidableAsync(new Context());
		exception.expect(ExecutionException.class);
		exception.expectMessage("ClassCastException: java.lang.Void cannot be cast to java.io.Serializable");
		future.get();
	}

	private <S> S getProxy(Class<S> serviceClass, ClientServiceInvocationHandler handler) {
		@SuppressWarnings("unchecked")
		S res = (S) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ serviceClass }, handler);
		return res;
	}

	private ServiceInvoker remoteCallerMock(byte[] res) {
		ServiceInvoker requester = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context context = invocation.getArgument(1);
			return CompletableFuture.completedFuture(new SimpleEntry<>(context, res));
		}).when(requester).call(any(), any(), any());
		return requester;
	}
}
