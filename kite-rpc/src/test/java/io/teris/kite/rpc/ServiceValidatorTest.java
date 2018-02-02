/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.kite.rpc;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.teris.kite.Context;
import io.teris.kite.Name;
import io.teris.kite.Service;


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

		void foo(Context context);

		void foo(Context context, @Name("data") String data);
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
		exception.expect(InvocationException.class);
		exception.expectMessage("Service definition NotInterface must be an interface");
		ServiceValidator.validate(NotInterface.class);
	}

	@Test
	public void validate_NoMethods_throws() {
		exception.expect(InvocationException.class);
		exception.expectMessage("Service definition NoMethods must declare at least one method");
		ServiceValidator.validate(NoMethods.class);
	}

	@Test
	public void validate_Unannotated_throws() {
		exception.expect(InvocationException.class);
		exception.expectMessage("Missing @Service annotation on Unannotated");
		ServiceValidator.validate(Unannotated.class);
	}

	@Test
	public void validate_Overloading_throws() {
		exception.expect(InvocationException.class);
		exception.expectMessage("Service definition Overloading must not declare any overloaded methods (violation: foo)");
		ServiceValidator.validate(Overloading.class);
	}

	@Test
	public void validate_InvalidMethod_throws() {
		exception.expect(InvocationException.class);
		exception.expectMessage("First parameters to InvalidMethod.invalid must be Context");
		ServiceValidator.validate(InvalidMethod.class);
	}

	@Test
	public void validate_NonSerializable_throws() {
		exception.expect(InvocationException.class);
		exception.expectMessage("After Context all parameter types in NonSerializable.valid must implement Serializable");
		ServiceValidator.validate(NonSerializable.class);
	}

	@Test
	public void validate_Valid_success() {
		ServiceValidator.validate(Valid.class);
	}
}
