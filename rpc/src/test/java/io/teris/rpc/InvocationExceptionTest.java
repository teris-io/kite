/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;


public class InvocationExceptionTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	interface AService {

		void thrown();

		void caused();
	}


	@Test
	public void thrown_correctMessage() {
		AService service = Proxier.get(AService.class);
		exception.expect(InvocationException.class);
		exception.expectMessage("Failed to invoke AService.thrown");
		service.thrown();
	}

	@Test
	public void caused_correctMessage() {
		AService service = Proxier.get(AService.class);
		exception.expect(InvocationException.class);
		exception.expectMessage("Failed to invoke AService.caused [caused by NumberFormatException: For input string: \"bad\"]");
		service.caused();
	}

	private static class Proxier implements InvocationHandler {

		static <S> S get(Class<S> serviceClass) {
			@SuppressWarnings("unchecked")
			S res = (S) Proxy.newProxyInstance(Proxier.class.getClassLoader(), new Class[]{ serviceClass }, new Proxier());
			return res;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws InvocationException {
			if ("thrown".equals(method.getName())) {
				throw new InvocationException(method);
			}
			try {
				Integer.valueOf("bad");
				throw new AssertionError("unreachable code");
			}
			catch (Exception ex) {
				throw new InvocationException(method, ex);
			}
		}
	}

}
