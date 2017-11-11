/*
 * Copyright (c) teris.io & Oleg Sklyar, 2017. All rights reserved
 */

package io.teris.rpc.context;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class CallerContext implements Serializable {

	public Map<String, String> toMap() {
		return new HashMap<>();
	}


}
