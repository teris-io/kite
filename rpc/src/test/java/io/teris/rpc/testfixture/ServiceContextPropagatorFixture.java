/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.testfixture;

import javax.annotation.Nonnull;

import io.teris.rpc.Context;
import io.teris.rpc.ContextAware;


public class ServiceContextPropagatorFixture {

	public interface AService {
		int a();
	}

	public static class AImpl implements AService {

		public Context context = new Context();

		private int val = 1;

		public AImpl() {}

		public AImpl(AImpl instance) {
			this.val = instance.val + 1;
		}

		@Override
		public int a() {
			return val;
		}
	}

	public interface BService {
		int b();
	}

	public static class BImpl implements BService {

		private Context context = new Context();

		public AService a = new AImpl();

		private int val = 10;

		public BImpl() {}

		public BImpl(BImpl instance, Context context) {
			this.val = instance.val + 10;
			this.context = context;
			this.a = instance.a;
		}

		@Override
		public int b() {
			return val + a.a();
		}
	}

	public interface CService {
		int c();
	}

	public static class CImpl implements CService {

		private Context context = new Context();

		public BService b = new BImpl();

		private int val = 100;

		public CImpl() {}

		public CImpl(CImpl instance) {
			this.val = instance.val + 100;
			this.b = instance.b;
		}

		@Override
		public int c() {
			return val + b.b();
		}

		public Context getContext() {
			return context;
		}

		public void setContext(Context context) {
			this.context = context;
		}
	}

	public interface DService {
		int d();
	}

	public static class DImpl implements DService, ContextAware {

		private Context context = new Context();

		public CService c = new CImpl();

		private int val = 1000;

		@Override
		public int d() {
			return val + c.c();
		}

		@Nonnull
		@Override
		public Context getContext() {
			return context;
		}

		@Override
		public void setContext(@Nonnull Context context) {
			this.context = context;
		}

		@Nonnull
		@Override
		public DImpl newInContext(@Nonnull Context context) {
			DImpl d = new DImpl();
			d.val = val + 1000;
			d.c = c;
			d.context = context;
			return d;
		}
	}
}
