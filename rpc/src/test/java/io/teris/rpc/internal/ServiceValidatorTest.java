/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.internal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.rpc.Context;
import io.teris.rpc.Name;
import io.teris.rpc.Service;


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
	public void validate_NotInterface_throws() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Service definition 'NotInterface' must be an interface");
		ServiceValidator.validate(NotInterface.class);
	}

	@Test
	public void validate_NoMethods_throws() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Service definition 'NoMethods' must define at least one method");
		ServiceValidator.validate(NoMethods.class);
	}

	@Test
	public void validate_Unannotated_throws() {
		exception.expect(IllegalStateException.class);
		exception.expectMessage("Service 'Unannotated' must be annotation with @Service");
		ServiceValidator.validate(Unannotated.class);
	}

	@Test
	public void validate_InvalidMethod_throws() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("First argument of the service method 'invalid' must be Context");
		ServiceValidator.validate(InvalidMethod.class);
	}

	@Test
	public void validate_NonSerializable_throws() {
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Argument types of the service method 'valid' must implement Serializable or be void");
		ServiceValidator.validate(NonSerializable.class);
	}

	@Test
	public void validate_Valid_success() {
		ServiceValidator.validate(Valid.class);
	}
}
