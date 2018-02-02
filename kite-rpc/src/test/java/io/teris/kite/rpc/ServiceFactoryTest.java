/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.kite.Context;
import io.teris.kite.Deserializer;
import io.teris.kite.Serializer;
import io.teris.kite.Service;
import io.teris.kite.rpc.testfixture.TestSerializer;


public class ServiceFactoryTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void builder_serializerAndServiceInvoker_success() {
		ServiceFactory.invoker(mock(ServiceInvoker.class))
			.serializer(mock(Serializer.class))
			.build();
	}

	@Test
	public void builder_allOptions_success() {
		Map<String, Deserializer> deserializerMap = new HashMap<>();
		ServiceFactory.invoker(mock(ServiceInvoker.class))
			.serializer(mock(Serializer.class))
			.deserializer("text", mock(Deserializer.class))
			.deserializers(deserializerMap)
			.uidGenerator(() -> "myUniqueId")
			.build();
	}

	@Service
	public interface SomeService {

		String method(Context context);
	}

	@Test
	public void newInstance_goodServiceDef_success() {
		ServiceFactory factory = ServiceFactory.invoker(mock(ServiceInvoker.class))
			.serializer(new TestSerializer())
			.build();

		factory.newInstance(SomeService.class);
	}

	@Test
	public void newInstance_invocation_successAndUidGenerator() {
		Context context = new Context();
		Entry<Context, byte[]> payload = new SimpleEntry<>(context, "{\"payload\":\"foo\"}".getBytes());
		ServiceInvoker invoker = mock(ServiceInvoker.class);
		doReturn(CompletableFuture.completedFuture(payload)).when(invoker).call(anyString(), any(), isNull());
		ServiceFactory factory = ServiceFactory.invoker(invoker)
			.serializer(new TestSerializer())
			.uidGenerator(() -> "someId")
			.build();

		SomeService service = factory.newInstance(SomeService.class);
		String res = service.method(context);
		assertEquals("foo", res);
		assertEquals("someId", context.get(Context.X_REQUEST_ID_KEY));
	}
}
