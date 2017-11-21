/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.testfixture.TestSerializer;


public class ServiceProxyInvocationHandlerTest {

	private static final TestSerializer serializer = new TestSerializer();

	private static final Supplier<String> uidGenerator = () -> UUID.randomUUID().toString();

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void constructor_missingServiceInvoker_throws() {
		exception.expect(NullPointerException.class);
		exception.expectMessage("Service invoker is required");
		new ServiceProxyInvocationHandler(null, null, Collections.emptyMap(), null);
	}

	@Test
	public void constructor_missingSerializer_throws() {
		exception.expect(NullPointerException.class);
		exception.expectMessage("Serializer is required");
		new ServiceProxyInvocationHandler(mock(ServiceInvoker.class), null, Collections.emptyMap(), null);
	}

	@Test
	public void constructor_missingUidGenerator_throws() {
		exception.expect(NullPointerException.class);
		exception.expectMessage("Unique Id generator is required");
		new ServiceProxyInvocationHandler(mock(ServiceInvoker.class), serializer, Collections.emptyMap(), null);
	}

	@Test
	public void constructor_missingDeserializerMap_success() {
		new ServiceProxyInvocationHandler(mock(ServiceInvoker.class), serializer, null, uidGenerator);
	}

	@Test
	public void constructor_withDeserializerMap_success() {
		Map<String, Deserializer> deserializerMap = new HashMap<>();
		deserializerMap.put(serializer.contentType(), serializer.deserializer());
		new ServiceProxyInvocationHandler(mock(ServiceInvoker.class), serializer, deserializerMap, uidGenerator);
	}

	@Service(replace = "io.teris.rpc.ServiceProxyInvocationHandlerTest.Some")
	public interface SomeService {

		CompletableFuture<String> asyncMethod(Context context);

		Future<String> brokenMethod(Context context);

		Double sqrt(Context context, @Name("a") Double a) throws IOException;

		Object brokenReturn(Context context);

		void brokenArg(Context context, Object arg);

		@Name("")
		void brokenRoute(Context context);
	}

	@Test
	public void invoke_async_completed() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":\"hello\"}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), isNull());

		Method method = SomeService.class.getDeclaredMethod("asyncMethod", Context.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		Object promise = handler.invoke(mock(SomeService.class), method, new Object[]{ new Context() });
		assertTrue(promise instanceof CompletableFuture);
		Object res = ((CompletableFuture<?>) promise).get(5, TimeUnit.SECONDS);
		assertEquals("hello", res);
	}

	@Test
	public void invoke_async_futureToReturn_completedExceptionally() throws Throwable {
		ServiceInvoker invoker = mock(ServiceInvoker.class);

		Method method = SomeService.class.getDeclaredMethod("brokenMethod", Context.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		Object promise = handler.invoke(mock(SomeService.class), method, new Object[]{ new Context() });
		assertTrue(promise instanceof CompletableFuture);

		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: Return type of SomeService.brokenMethod must implement Serializable or be void/Void (or a CompletableFuture thereof)");
		((CompletableFuture<?>) promise).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void invoke_async_completableFutureToReturn_onException_completedExceptionally() throws Throwable {
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.supplyAsync(() -> {
			throw new InvocationException("boom");
		})).when(invoker).call(anyString(), any(), isNull());

		Method method = SomeService.class.getDeclaredMethod("asyncMethod", Context.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		Object promise = handler.invoke(mock(SomeService.class), method, new Object[]{ new Context() });
		assertTrue(promise instanceof CompletableFuture);

		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: boom");
		((CompletableFuture<?>) promise).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void invoke_sync_success() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		Object res = handler.invoke(mock(SomeService.class), method, new Object[]{ new Context(), Double.valueOf(10.139) });
		assertTrue(res instanceof Double);
		assertEquals(3.14159, ((Double) res).doubleValue(), 0.001);
	}

	@Test
	public void invoke_sync_runtimeException_throws() throws Throwable {
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
			promise.completeExceptionally(new IllegalArgumentException("boom"));
			return promise;
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);

		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("boom");
		handler.invoke(mock(SomeService.class), method, new Object[]{ new Context(), Double.valueOf(10.139) });
	}

	@Test
	public void invoke_sync_completionException_throwsCause() throws Throwable {
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.supplyAsync(() -> {
			throw new InvocationException("boom");
		})).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);

		exception.expect(InvocationException.class);
		exception.expectMessage("boom");
		handler.invoke(mock(SomeService.class), method, new Object[]{ new Context(), Double.valueOf(10.139) });
	}

	@Test
	public void invoke_sync_otherException_throwsInvocationException() throws Throwable{
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			CompletableFuture<Entry<Context, byte[]>> promise = new CompletableFuture<>();
			promise.completeExceptionally(new IOException("boom"));
			return promise;
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		InvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);

		exception.expect(InvocationException.class);
		exception.expectMessage("IOException: boom");
		handler.invoke(mock(SomeService.class), method, new Object[]{ new Context(), Double.valueOf(10.139) });
	}

	@Test
	public void callRemote_async_success() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":\"hello\"}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), isNull());

		Method method = SomeService.class.getDeclaredMethod("asyncMethod", Context.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		CompletableFuture<String> promise = handler.callRemote(method, new Object[]{ new Context() });
		assertEquals("hello", promise.get(5, TimeUnit.SECONDS));
	}

	@Test
	public void callRemote_sync_success() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		CompletableFuture<Double> promise = handler.callRemote(method, new Object[]{ new Context(), Double.valueOf(10.139) });
		assertEquals(3.14159, promise.get(5, TimeUnit.SECONDS).doubleValue(), 0.001);
	}

	@Test
	public void callRemote_wrongReturn_completesWithInvocationException() throws Throwable {
		Method method = SomeService.class.getDeclaredMethod("brokenReturn", Context.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(mock(ServiceInvoker.class),
			serializer, null, uidGenerator);

		exception.expect(ExecutionException.class);
		exception.expectMessage("Return type of SomeService.brokenReturn must implement Serializable or be void/Void (or a CompletableFuture thereof)");
		handler.callRemote(method, new Object[]{ new Context() }).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void callRemote_wrongArgs_completesWithInvocationException() throws Throwable {
		Method method = SomeService.class.getDeclaredMethod("brokenArg", Context.class, Object.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(mock(ServiceInvoker.class),
			serializer, null, uidGenerator);

		exception.expect(ExecutionException.class);
		exception.expectMessage("After Context all parameter types in SomeService.brokenArg must implement Serializable");
		handler.callRemote(method, new Object[]{ new Context(), new Object() }).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void callRemote_wrongRoute_completesWithInvocationException() throws Throwable {
		Method method = SomeService.class.getDeclaredMethod("brokenRoute", Context.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(mock(ServiceInvoker.class),
			serializer, null, uidGenerator);

		exception.expect(ExecutionException.class);
		exception.expectMessage("Empty route for SomeService.brokenRoute");
		handler.callRemote(method, new Object[]{ new Context() }).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void callRemote_nullPayload_passesNullToInvoker() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":\"hello\"}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), isNull());

		Method method = SomeService.class.getDeclaredMethod("asyncMethod", Context.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{ new Context() }).get(5, TimeUnit.SECONDS);
		verify(invoker).call(anyString(), any(), isNull());
	}

	@Test
	public void callRemote_payload_passesSerializedToInvoker() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{ new Context(), Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
		verify(invoker).call(anyString(), any(), eq("{\"a\":10.139}".getBytes()));
	}

	@Test
	public void callRemote_route_passesToInvoker() throws Throwable {
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(new Context(), "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{ new Context(), Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
		verify(invoker).call(eq("sqrt"), any(), any());
	}

	@Test
	public void callRemote_requestId_passesNewValueInContextToInvoker() throws Throwable {
		Context context = new Context();
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(context, "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context requestContext = invocation.getArgument(1);
			assertEquals(0, context.size());
			assertEquals(2, requestContext.size());
			assertTrue(requestContext.containsKey(Context.X_REQUEST_ID_KEY));
			return CompletableFuture.completedFuture(payload);
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{ context, Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
		verify(invoker).call(anyString(), any(), any());
	}

	@Test
	public void callRemote_contentType_passesSerializerValueInContextToInvoker() throws Throwable {
		Context context = new Context();
		context.put(Context.CONTENT_TYPE_KEY, "random");
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(context, "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context requestContext = invocation.getArgument(1);
			assertEquals(1, context.size());
			assertEquals(2, requestContext.size());
			assertEquals(serializer.contentType(), requestContext.get(Context.CONTENT_TYPE_KEY));
			return CompletableFuture.completedFuture(payload);
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{ context, Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
		verify(invoker).call(anyString(), any(), any());
	}

	@Test
	public void callRemote_context_passesCopyToInvoker() throws Throwable {
		Context context = new Context();
		context.put("key", "value");
		context.put(Context.CONTENT_TYPE_KEY, "random");
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(context, "{\"payload\":3.14159}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context requestContext = invocation.getArgument(1);
			assertEquals(2, context.size());
			assertEquals(3, requestContext.size());
			assertEquals("value", requestContext.get("key"));
			assertNotSame(context, requestContext);
			return CompletableFuture.completedFuture(payload);
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{ context, Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
		verify(invoker).call(anyString(), any(), any());
	}

	@Test
	public void callRemote_payload_serializationIssue_completesExceptionally() throws Throwable {
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		Serializer mockedSerializer = mock(Serializer.class);
		doReturn("application/json").when(mockedSerializer).contentType();
		doReturn(serializer.deserializer()).when(mockedSerializer).deserializer();
		doReturn(CompletableFuture.supplyAsync(() -> {
			throw new IllegalArgumentException("boom");
		})).when(mockedSerializer).serialize(any());


		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			mockedSerializer, null, uidGenerator);

		exception.expect(ExecutionException.class);
		exception.expectMessage("IllegalArgumentException: boom");
		handler.callRemote(method, new Object[]{ new Context(), Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void callRemote_noInvokerException_contextUpdatedFromResponse() throws Throwable {
		Context context = new Context();
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context requestContext = invocation.getArgument(1);
			requestContext.put("key", "value");
			SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(requestContext, "{\"payload\":3.14159}".getBytes());
			return CompletableFuture.completedFuture(payload);
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		handler.callRemote(method, new Object[]{context, Double.valueOf(10.139) }).get(5, TimeUnit.SECONDS);
		assertEquals("value", context.get("key"));
	}

	@Test
	public void callRemote_errorInPayload_contextUpdatedFromResponse() throws Throwable {
		Context context = new Context();
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context requestContext = invocation.getArgument(1);
			requestContext.put("key", "value");
			SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(requestContext, "rubbish".getBytes());
			return CompletableFuture.completedFuture(payload);
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		try {
			handler.callRemote(method, new Object[]{context, Double.valueOf(10.139)}).get(5, TimeUnit.SECONDS);
			throw new AssertionError("unreachable code");
		}
		catch (ExecutionException ex) {
			assertEquals("value", context.get("key"));
		}
	}

	@Test
	public void callRemote_responseDataMissing_assumesSuccessWithNull() throws Throwable {
		Context context = new Context();
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(context, "{}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		Serializable res = handler.callRemote(method, new Object[]{context, Double.valueOf(10.139)}).get(5, TimeUnit.SECONDS);
		assertNull(res);
	}

	@Test
	public void callRemote_responseDataEmpty_assumesSuccessWithNull() throws Throwable {
		Context context = new Context();
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(context, null);
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		Serializable res = handler.callRemote(method, new Object[]{context, Double.valueOf(10.139)}).get(5, TimeUnit.SECONDS);
		assertNull(res);
	}

	@Test
	public void callRemote_responseDataDeserialized_onError_completesExeptionallyWithDeserialized() throws Throwable {

		HashMap<String, Serializable> response = new HashMap<>();
		response.put(ResponseFields.EXCEPTION, new ExceptionDataHolder(new InvocationException("boom")));

		Context context = new Context();
		SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(context, serializer.serialize(response).get(5, TimeUnit.SECONDS));

		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), any(byte[].class));

		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			serializer, null, uidGenerator);
		exception.expect(ExecutionException.class);
		exception.expectMessage("InvocationException: boom");
		handler.callRemote(method, new Object[]{context, Double.valueOf(10.139)}).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void callRemote_responseDataDeserialized_fromDifferentContentType_success() throws Throwable {
		Context context = new Context();
		Serializer mockedSerializer = mock(Serializer.class);
		doReturn(serializer.contentType()).when(mockedSerializer).contentType();
		doAnswer(invocation -> serializer.serialize(invocation.getArgument(0))).when(mockedSerializer).serialize(any());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doAnswer(invocation -> {
			Context requestContext = invocation.getArgument(1);
			requestContext.put(Context.CONTENT_TYPE_KEY, "text");
			SimpleEntry<Context, byte[]> payload = new SimpleEntry<>(requestContext, "{\"payload\":3.14159}".getBytes());
			return CompletableFuture.completedFuture(payload);
		}).when(invoker).call(anyString(), any(), any(byte[].class));

		Map<String, Deserializer> deserializerMap = new HashMap<>();
		deserializerMap.put("text", serializer.deserializer());
		Method method = SomeService.class.getDeclaredMethod("sqrt", Context.class, Double.class);
		ServiceProxyInvocationHandler handler = new ServiceProxyInvocationHandler(invoker,
			mockedSerializer, deserializerMap, uidGenerator);
		Double res = (Double) handler.callRemote(method, new Object[]{context, Double.valueOf(10.139)}).get(5, TimeUnit.SECONDS);
		assertEquals(3.14159, res.doubleValue(), 0.01);
	}
}
