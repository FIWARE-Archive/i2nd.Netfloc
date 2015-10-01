/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netfloc.iface;

public interface INetworkPathListener {
	public void networkPathCreated(INetworkPath np);
	public void networkPathUpdated(INetworkPath np);
	public void networkPathDeleted(INetworkPath np);
}