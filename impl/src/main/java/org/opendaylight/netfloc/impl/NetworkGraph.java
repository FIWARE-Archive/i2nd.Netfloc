/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package ch.icclab.netfloc.impl;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.neutron.spi.NeutronPort;
import org.opendaylight.neutron.spi.NeutronSubnet;
import org.opendaylight.neutron.spi.NeutronNetwork;
import org.opendaylight.neutron.spi.NeutronRouter;
import org.opendaylight.neutron.spi.NeutronFloatingIP;
import ch.icclab.netfloc.iface.IBridgeIterator;
import ch.icclab.netfloc.iface.IBridgeOperator;
import ch.icclab.netfloc.iface.ILinkPort;
import ch.icclab.netfloc.iface.INetworkOperator;
import ch.icclab.netfloc.iface.INetworkPath;
import ch.icclab.netfloc.iface.INetworkTraverser;
import ch.icclab.netfloc.iface.INodeOperator;
import ch.icclab.netfloc.iface.IPortOperator;
import ch.icclab.netfloc.iface.IHostPort;
import ch.icclab.netfloc.iface.ITraversableBridge;
import ch.icclab.netfloc.iface.INetworkPathListener;
import ch.icclab.netfloc.iface.nbhandlers.INeutronPortHandler;
import ch.icclab.netfloc.iface.nbhandlers.INeutronSubnetHandler;
import ch.icclab.netfloc.iface.nbhandlers.INeutronNetworkHandler;
import ch.icclab.netfloc.iface.nbhandlers.INeutronRouterHandler;
import ch.icclab.netfloc.iface.nbhandlers.INeutronFloatingIPHandler;
import ch.icclab.netfloc.iface.sbhandlers.IBridgeHandler;
import ch.icclab.netfloc.iface.sbhandlers.INodeHandler;
import ch.icclab.netfloc.iface.sbhandlers.IPortHandler;
import ch.icclab.netfloc.iface.ofhandlers.ILinkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.util.HashMap;
import java.lang.System;
import java.lang.IllegalStateException;

public class NetworkGraph implements
	INetworkTraverser,
	INetworkOperator {
	
	static final Logger logger = LoggerFactory.getLogger(NetworkGraph.class);

	List<INodeOperator> nodes = new LinkedList<INodeOperator>();
	private List<INetworkPathListener> networkPathListeners = new LinkedList<INetworkPathListener>();

	public void registerNetworkPathListener(INetworkPathListener npl) {
		this.networkPathListeners.add(npl);
	}

	public void notifyNetworkPathListenersCreate(INetworkPath networkPath) {	
		for (INetworkPathListener npl : this.networkPathListeners) {
			npl.networkPathCreated(networkPath);
		}
	}

	public void notifyNetworkPathListenersDelete(INetworkPath networkPath) {
		for (INetworkPathListener npl : this.networkPathListeners) {
			npl.networkPathDeleted(networkPath);
		}
	}

	public void notifyNetworkPathListenersUpdate(INetworkPath oldPath, INetworkPath newPath) {
		for (INetworkPathListener npl : this.networkPathListeners) {
			npl.networkPathUpdated(oldPath, newPath);
		}
	}

	public void traverse(IBridgeIterator bridgeIterator) {
		List<ITraversableBridge> bridgesToVisit = new LinkedList<ITraversableBridge>();
		bridgesToVisit.add(new TraversableBridge(this.getBridges().get(0)));
		this.traverseFromBridge(
			bridgesToVisit,
			bridgeIterator);
	}

	private void traverseFromBridge(
		List<ITraversableBridge> bridgesToVisit,
		IBridgeIterator bridgeIterator) {

		List<ITraversableBridge> visitedBridges = new LinkedList<ITraversableBridge>();

		while (!bridgesToVisit.isEmpty()) {

			ITraversableBridge currentBridge;
			List<ITraversableBridge> currentBridges = new LinkedList<ITraversableBridge>();

			while (!bridgesToVisit.isEmpty()) {
				currentBridge = bridgesToVisit.remove(0);

				if (!bridgeIterator.visitBridge(currentBridge)) {
					return;
				}
				
				visitedBridges.add(currentBridge);

				currentBridges.add(currentBridge);
			}

			for (ITraversableBridge bridgeToExtendFrom : currentBridges) {
				for (ITraversableBridge br : getAdjacentBridges(bridgeToExtendFrom)) {
					if (!visitedBridges.contains(br)) {
						bridgesToVisit.add(br);
					}
				}
			}
		}
	}

	public List<ITraversableBridge> getAdjacentBridges(ITraversableBridge br) {
		List<ITraversableBridge> adjacentBridges = new LinkedList<ITraversableBridge>();
		for (IPortOperator port : br.getBridge().getPorts()) {
			if (port instanceof ILinkPort) {
				adjacentBridges.add(new TraversableBridge(((ILinkPort)port).getLinkedPort().getBridge(), br));
			}
		}
		return adjacentBridges;
	}

	// unused, todo: revise
	public List<INetworkPath> getNetworkPaths() {
		List<INetworkPath> networkPaths1 = new LinkedList<INetworkPath>();
		List<IHostPort> hostPorts = new LinkedList<IHostPort>();
		for (IHostPort beginPort : hostPorts) {
			for (IHostPort endPort : hostPorts) {
				if (!beginPort.equals(endPort)) {
					networkPaths1.add(getNetworkPath(beginPort, endPort));
				}
			}
		}
		return networkPaths1;
	}

	public List<INetworkPath> getConnectableNetworkPaths(IHostPort src) {
		List<INetworkPath> paths = new LinkedList<INetworkPath>();
		for (IHostPort dst : this.getHostPorts()) {
			if (src.canConnectTo(dst)) {
				INetworkPath np = this.getNetworkPath(src, dst);
				if (np != null) {
					paths.add(np);
				}
			}
		}
		return paths;
	}

	public List<INetworkPath> getConnectableNetworkPaths(List<IHostPort> ports) {
		List<INetworkPath> paths = new LinkedList<INetworkPath>();
		for (IHostPort src : ports) {
			paths.addAll(this.getConnectableNetworkPaths(src));
		}
		return paths;
	}

	public INetworkPath getNetworkPath(final IHostPort begin, final IHostPort end) {
		ITraversableBridge beginBridge = new TraversableBridge(begin.getBridge());
		final ITraversableBridge endBridge = new TraversableBridge(end.getBridge());

		assert begin.getBridge() != null : "begin bridge can't be null";
		assert end.getBridge() != null : "end bridge can't be null";

		List<ITraversableBridge> bridgesToVisit = new LinkedList<ITraversableBridge>();
		
		bridgesToVisit.add(beginBridge);

		IBridgeIterator<INetworkPath> iterator = new IBridgeIterator<INetworkPath>() {
			
			private INetworkPath shortestPath = new NetworkPath(begin, end);
			private List<INetworkPath> paths = new LinkedList<INetworkPath>();
			private int iterations = 0;

			public boolean visitBridge(ITraversableBridge currentBridge) {
				iterations++;
				if (currentBridge.equals(endBridge)) {
					this.shortestPath = this.getRootPath(currentBridge);
					this.shortestPath.append(currentBridge.getBridge());
					this.shortestPath.close();
					return false;
				}

				INetworkPath newPath = new NetworkPath(begin, end);

				if (!paths.isEmpty()) {
					INetworkPath rootPath = this.getRootPath(currentBridge);
					newPath.addBridges(rootPath.getBridges());
				}

				newPath.append(currentBridge.getBridge());

				if (!paths.isEmpty()) {
					assert newPath.getLength() >= paths.get(0).getLength() : newPath.getLength() + " should >= " + paths.get(0).getLength() + " iterations: " + iterations;
				}
				paths.add(newPath);

				return true;
			}

			public INetworkPath getResult() {
				return (this.shortestPath.isClosed()) ? this.shortestPath : null;
			}

			private INetworkPath getRootPath(ITraversableBridge currentBridge) {
				for (INetworkPath path : this.paths) {
					if (path.getEnd().equals(currentBridge.getRoot().getBridge())) {
						return path;		
					}
				}
				assert false : "this should never happen";
				return null;
			}
		};

		this.traverseFromBridge(bridgesToVisit, iterator);
		INetworkPath resultPath = iterator.getResult();
		return resultPath;
	}

	// concurrency issue: links that are not established through LLDP
	private List<IHostPort> getHostPorts() {
		IBridgeIterator<List<IHostPort>> iterator = new IBridgeIterator<List<IHostPort>>() {
			
			private List<IHostPort> hostPorts = new LinkedList<IHostPort>();
			
			public boolean visitBridge(ITraversableBridge br) {
				hostPorts.addAll(br.getBridge().getHostPorts());
				return true;
			}
			
			public List<IHostPort> getResult() {
				return this.hostPorts;
			}
		};
		this.traverse(iterator);
		return iterator.getResult();
	}

	private void checkPossibleConnections(IHostPort srcPort) {
		for (IHostPort port : this.getHostPorts()) {
			if (srcPort.canConnectTo(port)) {
				this.notifyNetworkPathListenersCreate(this.getNetworkPath(srcPort, port));
			}
		}
	}

	public List<INodeOperator> getNodes() {
		return this.nodes;
	}

	public INodeOperator getNode(NodeId id) {

		if (id == null) {
			throw new IllegalArgumentException("node id cannot be null");
		}

		logger.info("search node: " + id.toString());

		for (INodeOperator nd : this.nodes) {
			NodeId ndid = nd.getNodeId();
			if (ndid == null) {
				throw new IllegalStateException("cached node id is null");
			}
			logger.info("compare node: " + ndid.toString());
			if (ndid.equals(id)) {
				return nd;
			}
		}
		return null;
	}

	public void addNode(INodeOperator node) {
		this.nodes.add(node);
	}

	public void removeNode(INodeOperator node) {
		this.nodes.remove(node);
	}

	public void removeNode(NodeId id) {
		INodeOperator node = this.getNode(id);
		if (node == null) {
			return;
		}
		this.nodes.remove(node);
	}

	public List<IBridgeOperator> getBridges() {
		List<IBridgeOperator> bridges = new LinkedList<IBridgeOperator>();
		for (INodeOperator node : this.getNodes()) {
			bridges.addAll(node.getBridges());
		}
		return bridges;
	}

	public void addPort(Node node, TerminationPoint tp, OvsdbTerminationPointAugmentation tpa) {
		IBridgeOperator bo = getParentBridge(node.getNodeId());

		if (bo == null) {
			throw new IllegalStateException("bridge is null on port create");
		}

		IPortOperator port = SouthboundHelper.maybeCreateInternalPort(bo, tp, tpa);

		if (port != null) {
			logger.info("is an internal port");
			bo.addPort(port);
			return;
		}

		logger.info("is a link port");
		port = new LinkPort(bo, tp, tpa);

		bo.addPort(port);
	}

	public void addPort(Node node, TerminationPoint tp, OvsdbTerminationPointAugmentation tpa, NeutronPort neutronPort) {
		IBridgeOperator bo = getParentBridge(node.getNodeId());

		if (bo == null) {
			throw new IllegalStateException("bridge is null on port create");
		}

		logger.info("is a neutron port");
		IHostPort port = new HostPort(bo, tp, tpa, neutronPort);
		bo.addPort(port);
	}

	public void removePort(Node node, OvsdbTerminationPointAugmentation tpa) {
		IBridgeOperator bo = this.getParentBridge(node.getNodeId());
		IPortOperator po = bo.getPort(tpa.getPortUuid());

		if (po instanceof IHostPort) {
			IHostPort hostPort = (IHostPort)po;
			for (INetworkPath removedPath : this.getConnectableNetworkPaths(hostPort)) {
				this.notifyNetworkPathListenersDelete(removedPath);
			}
		}

		bo.removePort(po);
	}

	public void updatePort(Node node, TerminationPoint tp, OvsdbTerminationPointAugmentation tpa) {
		IBridgeOperator bo = this.getParentBridge(node.getNodeId());
		IPortOperator po = bo.getPort(tpa.getPortUuid());
		po.update(tp, tpa);

		// todo test connections
	}

	// needs to be tested with ovs
	private IBridgeOperator getParentBridge(NodeId id) {
		// find the node and bridge of this port
		INodeOperator no = this.getParentNode(id);

		if (no == null) {
			throw new IllegalStateException("node is null on port create");
		}

		for (IBridgeOperator br : no.getBridges()) {
			if (br.getNodeId().equals(id)) {
				return br;
			}
		}
		return null;
	}

	public INodeOperator getParentNode(NodeId id) {
		if (id == null) {
			throw new IllegalArgumentException("node id cannot be null");
		}

		logger.info("search node: " + id.getValue());

		for (INodeOperator nd : this.nodes) {
			NodeId ndid = nd.getNodeId();
			if (ndid == null) {
				throw new IllegalStateException("cached node id is null");
			}
			logger.info("compare node: " + ndid.getValue());
			if (id.getValue().contains(ndid.getValue())) {
				return nd;
			}
		}
		return null;
	}

	public void createLink(Link link) {
		TpId tpIdSrc = link.getSource().getSourceTp();
		TpId tpIdDst = link.getDestination().getDestTp();

		if (tpIdSrc == null || tpIdDst == null) {
			logger.error("TpId is null for src: <{}>, dst: <{}>", tpIdSrc, tpIdDst);
		}

		ILinkPort portSrc = this.getLinkPort(tpIdSrc);
		ILinkPort portDst = this.getLinkPort(tpIdDst);

		portSrc.setLinkedPort(portDst);

		// todo: update network paths?
	}

	public void deleteLink(Link link) {
		TpId tpIdSrc = link.getSource().getSourceTp();
		TpId tpIdDst = link.getDestination().getDestTp();

		if (tpIdSrc == null || tpIdDst == null) {
			logger.error("TpId is null for <{}>, <{}>", tpIdSrc, tpIdDst);
		}

		ILinkPort portSrc = this.getLinkPort(tpIdSrc);
		ILinkPort portDst = this.getLinkPort(tpIdDst);

		// old paths
		List<INetworkPath> oldPaths = this.getConnectableNetworkPaths(this.getHostPorts());

		portSrc.removeLinkedPort(portDst);
		
		// notify network path listeners by comparing old to new path list
		List<INetworkPath> newPaths = this.getConnectableNetworkPaths(this.getHostPorts());
		
		for (INetworkPath oldPath : oldPaths) {
			boolean found = false;
			for (INetworkPath newPath : newPaths) {
				if (oldPath.isEqualConnection(newPath)) {
					found = true;

					if (!oldPath.equals(newPath)) {
						// link is broken but connection can be recovered
						this.notifyNetworkPathListenersUpdate(oldPath, newPath);
					}
				}
			}
			if (!found) {
				// this is a broken link and connection cannot be recovered
				this.notifyNetworkPathListenersDelete(oldPath);
			}
		}
	}

	public ILinkPort getLinkPort(TpId tpid) {
		for (IBridgeOperator br : this.getBridges()) {
			for (ILinkPort port : br.getLinkPorts()) {
				if (port.isOvsPort(tpid)) {
					return port;
				}
			}
		}
		return null;
	}
}