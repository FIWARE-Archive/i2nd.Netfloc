/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netfloc.impl;

import org.opendaylight.netfloc.iface.IBridgeOperator;
import org.opendaylight.netfloc.iface.ITraversableBridge;

public class TraversableBridge implements ITraversableBridge {
	private ITraversableBridge root;
	private IBridgeOperator bridge;

	public TraversableBridge(IBridgeOperator bridge) {
		this.bridge = bridge;
	}

	public TraversableBridge(IBridgeOperator bridge, ITraversableBridge root) {
		this.bridge = bridge;
		this.root = root;
	}

	public ITraversableBridge getRoot() {
		return this.root;
	}

	public void setRoot(ITraversableBridge root) {
		this.root = root;
	}

	public IBridgeOperator getBridge() {
		return this.bridge;
	}

	// traverse implementation checks on euqals if a bridge is visited
	public boolean equals(Object o) {
		if (!(o instanceof ITraversableBridge)) {
			return false;
		}
		ITraversableBridge tb = (ITraversableBridge)o;
		assert (tb != null) : "should not be null";
		IBridgeOperator bo = tb.getBridge();
		assert (bo != null) : "should not be null";
		IBridgeOperator br = this.getBridge();
		assert (br != null) : "should not be null";
		return bo.equals(br);
		//return o instanceof ITraversableBridge && ((ITraversableBridge)o).getBridge().equals(this.getBridge());
	}
}