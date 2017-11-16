/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc;

import java.io.Serializable;


public class ExceptionDataHolder implements Serializable {

	static final long serialVersionUID = 23489765234056L;

	protected ExceptionDataHolder() {}

	public ExceptionDataHolder(ServiceException ex) {
		message = ex.getMessage();
		type = ex.getClass().getSimpleName();
		stackTrace = ex.getStackTrace();
	}

	public ExceptionDataHolder(Throwable t) {
		this(new ServiceException(t));
	}

	public String message = null;

	public String type = ServiceException.class.getSimpleName();

	public StackTraceElement[] stackTrace = new StackTraceElement[]{};

	public ServiceException exception() {
		if (BusinessException.class.getSimpleName().equalsIgnoreCase(type)) {
			return new BusinessException(message, stackTrace);
		}
		else if (TechnicalException.class.getSimpleName().equalsIgnoreCase(type)) {
			return new TechnicalException(message, stackTrace);
		}
		return new ServiceException(message, stackTrace);
	}
}
