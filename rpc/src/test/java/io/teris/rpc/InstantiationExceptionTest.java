/**
 * Copyright (c) Profidata AG 2017
 */
package io.teris.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class InstantiationExceptionTest {

	interface AService {}

	private <S> void factory(Class<S> serviceClass) throws ServiceException {
		try {
			Integer.valueOf("bad");
		}
		catch (Exception ex) {
			throw new InstantiationException(serviceClass, ex);
		}
		throw new AssertionError("unreachable code");
	}

	@Test
	public void thrown_correctMessage_success() {
		try {
			factory(AService.class);
			throw new AssertionError("unreachable code");
		}
		catch (ServiceException ex) {
			assertTrue(ex instanceof InstantiationException);
			assertEquals("Failed to construct an instance of service AService [caused by NumberFormatException: For input string: \"bad\"]", ex.getMessage());
			assertTrue(ex.getStackTrace()[0].toString().contains("java.lang.NumberFormatException.forInputString(NumberFormatException.java"));
		}
	}
}
