/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.testfixture.TestSerializer;


public class ServiceDispatcherTest {

	private static final Serializer serializer = new TestSerializer();

	private static final Deserializer deserializer = serializer.deserializer();

	@Rule
	public ExpectedException exception = ExpectedException.none();

	public class Response implements Serializable {

		public Serializable payload;

		public ExceptionDataHolder exception;
	}

	@Service(value="some")
	public interface SomeService {
		CompletableFuture<String> async(Context context, @Name("value") String value);

		String sync(Context context, @Name("value") String value) throws IOException;

		void voidMethod(Context context, @Name("value") String value) throws IOException;

		Void aVoidMethod(Context context, @Name("value") String value) throws IOException;
	}

	@Service(value="other")
	public interface OtherService {
		String sync(Context context, @Name("value") String value) throws IOException;
	}

	@Test
	public void builder_acceptsAll_success() {
		Map<String, Deserializer> deserializerMap = new HashMap<>();
		deserializerMap.put("some content", serializer.deserializer());
		ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, mock(SomeService.class))
			.bind(OtherService.class, mock(OtherService.class))
			.deserializer("text", serializer.deserializer())
			.deserializers(deserializerMap)
			.build();
	}

	@Test
	public void constructor_serializerNull_throws() {
		exception.expect(NullPointerException.class);
		exception.expectMessage("Serializer is required");
		new ServiceDispatcherImpl(Collections.emptyMap(), null, Collections.emptyMap());
	}

	@Test
	public void dispatchRoutes() {
		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, mock(SomeService.class))
			.bind(OtherService.class, mock(OtherService.class))
			.build();
		assertEquals(new TreeSet<>(Arrays.asList("other.sync", "some.async", "some.avoidmethod", "some.sync", "some.voidmethod")), dispatcher.dispatchRoutes());
	}


	@Test
	public void call_async_success() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doReturn(CompletableFuture.completedFuture("boo")).when(serviceImpl).async(any(), any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.async", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertNull(response.exception);
		assertTrue(response.payload instanceof byte[]);
		assertEquals("\"boo\"", new String((byte[]) response.payload));
	}

	@Test
	public void call_sync_success() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doReturn("boo").when(serviceImpl).sync(any(), any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.sync", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertNull(response.exception);
		assertTrue(response.payload instanceof byte[]);
		assertEquals("\"boo\"", new String((byte[]) response.payload));
	}

	@Test
	public void call_void_success() throws Exception {
		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, mock(SomeService.class))
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.voidmethod", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertNull(response.payload);
		assertNull(response.exception);
	}

	@Test
	public void call_Void_success() throws Exception {
		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, mock(SomeService.class))
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.avoidmethod", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertNull(response.payload);
		assertNull(response.exception);
	}

	@Test
	public void call_noRoute_exceptionWrappedIntoExDataHolder() throws Exception {
		ServiceDispatcher dispatcher = new ServiceDispatcherImpl(Collections.emptyMap(), new TestSerializer(), Collections.emptyMap());
		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.route", new Context(), new byte[]{});
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertTrue(response.exception.exception() instanceof InvocationException);
		assertEquals("No route to some.route", response.exception.message);
	}

	@Test
	public void call_async_businessLogicCompletesExceptionally_exceptionWrappedIntoExDataHolder() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doReturn(CompletableFuture.supplyAsync(() -> {
			throw new RuntimeException("boom");
		})).when(serviceImpl).async(any(), any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.async", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertTrue(response.exception.exception() instanceof BusinessException);
		assertEquals("RuntimeException: boom", response.exception.message);
	}

	@Test
	public void call_async_businessLogicRuntimeException_exceptionWrappedIntoExDataHolder() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doThrow(new RuntimeException("boom")).when(serviceImpl).async(any(), any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.async", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertTrue(response.exception.exception() instanceof BusinessException);
		assertEquals("RuntimeException: boom", response.exception.message);
	}

	@Test
	public void call_sync_businessLogicRuntimeException_exceptionWrappedIntoExDataHolder() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doThrow(new RuntimeException("boom")).when(serviceImpl).sync(any(), any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.sync", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertTrue(response.exception.exception() instanceof BusinessException);
		assertEquals("RuntimeException: boom", response.exception.message);
	}

	@Test
	public void call_sync_businessLogicCheckedException_exceptionWrappedIntoExDataHolder() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doThrow(new IOException("boom")).when(serviceImpl).sync(any(), any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(serializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.sync", new Context(), "{\"value\":\"foo\"}".getBytes());
		Entry<Context, byte[]> res = promise.get(5, TimeUnit.SECONDS);
		Response response = deserializer.deserialize(res.getValue(), Response.class).get(5, TimeUnit.SECONDS);
		assertTrue(response.exception.exception() instanceof BusinessException);
		assertEquals("IOException: boom", response.exception.message);
	}

	@Test
	public void call_sync_businessViaolatesServiceRestrictions_completesExceptionally() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doReturn("foo").when(serviceImpl).sync(any(), any());

		Serializer mockedSerializer = mock(Serializer.class);
		doReturn(serializer.contentType()).when(mockedSerializer).contentType();
		doThrow(new RuntimeException("boom")).when(mockedSerializer).serialize(any());

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(mockedSerializer)
			.deserializer(serializer.contentType(), serializer.deserializer())
			.bind(SomeService.class, serviceImpl)
			.build();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.sync", new Context(), null);

		exception.expect(ExecutionException.class);
		exception.expectMessage("RuntimeException: boom");
		promise.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void call_updates_contextWithSerializerContentType() throws Exception {
		SomeService serviceImpl = mock(SomeService.class);
		doReturn("boo").when(serviceImpl).sync(any(), any());

		Serializer mockedSerializer = mock(Serializer.class);
		doReturn("text").when(mockedSerializer).contentType();
		doAnswer(invocation -> serializer.serialize(invocation.getArgument(0))).when(mockedSerializer).serialize(any());
		doReturn(serializer.deserializer()).when(mockedSerializer).deserializer();

		ServiceDispatcher dispatcher = ServiceDispatcher.builder()
			.serializer(mockedSerializer)
			.bind(SomeService.class, serviceImpl)
			.build();

		Context context = new Context();

		CompletableFuture<Entry<Context, byte[]>> promise =	dispatcher.call("some.sync", context, "{\"value\":\"foo\"}".getBytes());
		promise.get(5, TimeUnit.SECONDS);
		assertEquals("text", context.get(Context.CONTENT_TYPE_KEY));
	}
}
