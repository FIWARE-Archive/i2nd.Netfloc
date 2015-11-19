/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package ch.icclab.netfloc.impl;

import java.util.List;

import ch.icclab.netfloc.iface.IBridgeOperator;
import ch.icclab.netfloc.iface.ILinkPort;
import ch.icclab.netfloc.iface.INetworkPath;
import ch.icclab.netfloc.iface.IHostPort;

import java.util.LinkedList;

public class NetworkPath implements INetworkPath {

	private List<IBridgeOperator> bridges = new LinkedList<IBridgeOperator>();
	private IHostPort beginPort;
	private IHostPort endPort;
	private boolean closed = false;

	public NetworkPath(IHostPort beginPort, IHostPort endPort) {
		this.beginPort = beginPort;
		this.endPort = endPort;
	}

	public void close() {
		this.closed = true;
	}

	public boolean isClosed() {
		return this.closed;
	}

	public int getLength() {
		return bridges.size();
	}

	public IBridgeOperator getBegin() {
		return bridges.get(0);
	}

	public IBridgeOperator getEnd() {
		return bridges.get(bridges.size()-1);
	}

	public IHostPort getBeginPort() {
		return this.beginPort;
	}

	public IHostPort getEndPort() {
		return this.endPort;
	}

	public void append(IBridgeOperator bridge) {
		bridges.add(bridge);
	}

	public IBridgeOperator getPrevious(IBridgeOperator bridge) {
		int index = bridges.lastIndexOf(bridge);
		if (index > 0) {
			return bridges.get(index - 1);
		}
		return null;
	}

	public IBridgeOperator getNext(IBridgeOperator bridge) {
		int index = bridges.lastIndexOf(bridge);
		if (index < bridges.size() - 1) {
			return bridges.get(index + 1);
		}
		return null;
	}

	public ILinkPort getPreviousLink(IBridgeOperator bridge) {
		IBridgeOperator previousBridge = this.getPrevious(bridge);
		List<ILinkPort> possiblyLinkedPorts = previousBridge.getLinkPorts(); 
		for (ILinkPort port : bridge.getLinkPorts()) {
			if (possiblyLinkedPorts.contains(port.getLinkedPort())) {
				return port;
			}
		}
		return null;
	}

	public ILinkPort getNextLink(IBridgeOperator bridge) {
		IBridgeOperator nextBridge = this.getNext(bridge);
		List<ILinkPort> possiblyLinkedPorts = nextBridge.getLinkPorts(); 
		for (ILinkPort port : bridge.getLinkPorts()) {
			if (possiblyLinkedPorts.contains(port.getLinkedPort())) {
				return port;
			}
		}
		return null;
	}

	public List<IBridgeOperator> getBridges() {
		return bridges;
	}

	public void addBridges(List<IBridgeOperator> bridges) {
		this.bridges.addAll(bridges);
	}

	public INetworkPath getCleanPath() {
		if (!this.closed) {
			return null;
		}
		INetworkPath cleanPath = new NetworkPath(this.beginPort, this.endPort);
		List<IBridgeOperator> cleanBridges = new LinkedList<IBridgeOperator>();
		IBridgeOperator bridge = this.getBegin();
		boolean started = false;
		do {
			if (started) {
				bridge = this.getNext(bridge);
			}
			started = true;
			IBridgeOperator cbr = new Bridge(bridge.getParentNode(), bridge.getNode(), bridge.getAugmentation());

			// add vm ports
			if (bridge.equals(this.getBegin())) {
				cbr.addHostPort(this.beginPort);
			}

			if (bridge.equals(this.getEnd())) {
				cbr.addHostPort(this.endPort);
			}

			// add link port
			if (!bridge.equals(this.getEnd())) {
				cbr.addLinkPort(this.getNextLink(bridge));
			}

			cleanBridges.add(cbr);

		} while (!bridge.equals(this.getEnd()));
		cleanPath.addBridges(cleanBridges);
		cleanPath.close();
		return cleanPath;
	}

	public boolean isEqualConnection(INetworkPath np) {
		return (this.getBeginPort().equals(np.getBeginPort()) &&
			this.getEndPort().equals(np.getEndPort())) ||
			(this.getBeginPort().equals(np.getEndPort()) &&
			this.getEndPort().equals(np.getBeginPort()));
	}

	public boolean equals(Object o) {
		if (!(o instanceof NetworkPath)) {
			return false;
		}
		NetworkPath np = (NetworkPath)o;
		return this.isEqualConnection(np) && this.getBridges().equals(np.getBridges());
	}

	public int hashCode() {
		return 13 * (this.getBeginPort().hashCode() +
			this.getEndPort().hashCode() +
			this.getBridges().hashCode());
	}
}