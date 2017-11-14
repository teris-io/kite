/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Context;
import io.teris.rpc.InstantiationException;
import io.teris.rpc.Name;
import io.teris.rpc.Service;
import io.teris.rpc.ServiceException;


public class ServiceValidatorTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Service
	public class NotInterface {

		void valid1(Context context, @Name("data") Integer data) {};
	}

	@Service
	public interface NoMethods {
	}

	public interface Unannotated {

		void method(Context context);
	}

	@Service
	public interface Overloading {

		void method(Context context);

		void method(Context context, @Name("data") String data);
	}

	@Service
	public interface InvalidMethod {

		void valid(Context context, @Name("data") String data);

		void invalid(@Name("data") String data);
	}

	@Service
	public interface NonSerializable {

		void valid(Context context, @Name("data") Object data);

	}

	@Service
	public interface Valid {

		void valid1(Context context, @Name("data") Integer data);

		@Name("valid3")
		void valid2(Context context, @Name("data") String data);
	}

	@Test
	public void validate_NotInterface_throws() throws ServiceException {
		exception.expect(InstantiationException.class);
		exception.expectMessage("Failed to construct an instance of service NotInterface: service definition must be an interface");
		ServiceValidator.validate(NotInterface.class);
	}

	@Test
	public void validate_NoMethods_throws() throws ServiceException {
		exception.expect(InstantiationException.class);
		exception.expectMessage("Failed to construct an instance of service NoMethods: service definition must declare at least one service method");
		ServiceValidator.validate(NoMethods.class);
	}

	@Test
	public void validate_Unannotated_throws() throws ServiceException {
		exception.expect(InstantiationException.class);
		exception.expectMessage("Failed to construct an instance of service Unannotated [caused by InvocationException: Failed to invoke Unannotated.method: missing @Service annotation]");
		ServiceValidator.validate(Unannotated.class);
	}

	@Test
	public void validate_Overloading_throws() throws ServiceException {
		exception.expect(InstantiationException.class);
		exception.expectMessage("Failed to construct an instance of service Overloading: method overloading is not permitted (violation on method: method");
		ServiceValidator.validate(Overloading.class);
	}

	@Test
	public void validate_InvalidMethod_throws() throws ServiceException {
		exception.expect(InstantiationException.class);
		exception.expectMessage("Failed to construct an instance of service InvalidMethod [caused by InvocationException: Failed to invoke InvalidMethod.invalid: first argument must be an instance of Context]");
		ServiceValidator.validate(InvalidMethod.class);
	}

	@Test
	public void validate_NonSerializable_throws() throws ServiceException{
		exception.expect(InstantiationException.class);
		exception.expectMessage("Failed to construct an instance of service NonSerializable [caused by InvocationException: Failed to invoke NonSerializable.valid: arguments must implement Serializable]");
		ServiceValidator.validate(NonSerializable.class);
	}

	@Test
	public void validate_Valid_success() throws ServiceException {
		ServiceValidator.validate(Valid.class);
	}
}
