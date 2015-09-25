/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netfloc.impl;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.neutron.spi.NeutronPort;
import org.opendaylight.netfloc.iface.IBridgeIterator;
import org.opendaylight.netfloc.iface.IBridgeOperator;
import org.opendaylight.netfloc.iface.ILinkPort;
import org.opendaylight.netfloc.iface.INetworkOperator;
import org.opendaylight.netfloc.iface.INetworkPath;
import org.opendaylight.netfloc.iface.INetworkTraverser;
import org.opendaylight.netfloc.iface.INodeOperator;
import org.opendaylight.netfloc.iface.IPortOperator;
import org.opendaylight.netfloc.iface.ITenantNetworkOperator;
import org.opendaylight.netfloc.iface.IHostPort;
import org.opendaylight.netfloc.iface.ITraversableBridge;
import org.opendaylight.netfloc.iface.nbhandlers.INeutronPortHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IBridgeCreateHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IBridgeDeleteHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IBridgeUpdateHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IHandleResponse;
import org.opendaylight.netfloc.iface.sbhandlers.INodeConnectHandler;
import org.opendaylight.netfloc.iface.sbhandlers.INodeConnectionAttributeChangeHandler;
import org.opendaylight.netfloc.iface.sbhandlers.INodeDisconnectHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IPortCreateHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IPortDeleteHandler;
import org.opendaylight.netfloc.iface.sbhandlers.IPortUpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.util.HashMap;
import java.lang.System;
import java.lang.IllegalStateException;

// idk
public class NetworkGraph implements
	INetworkTraverser,
	INetworkOperator,
	IBridgeCreateHandler,
	IBridgeDeleteHandler,
	IBridgeUpdateHandler,
	INodeConnectHandler,
	INodeConnectionAttributeChangeHandler,
	INodeDisconnectHandler,
	IPortCreateHandler,
	IPortDeleteHandler,
	IPortUpdateHandler,
	INeutronPortHandler{
	
	static final Logger logger = LoggerFactory.getLogger(NetworkGraph.class);

	List<INodeOperator> nodes = new LinkedList<INodeOperator>();
	List<ITenantNetworkOperator> tenantNetworks = new LinkedList<ITenantNetworkOperator>();
	List<IHostPort> neutronPortCache = new LinkedList<IHostPort>();

	// BFS
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
					return false;
				}

				INetworkPath newPath = new NetworkPath(begin, end);

				if (!paths.isEmpty()) {
					INetworkPath rootPath = this.getRootPath(currentBridge);
					System.out.println("root path size: " + rootPath.getLength());
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
				return this.shortestPath;
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

	public List<ITenantNetworkOperator> getTenantNetworks() {
		return this.tenantNetworks;
	}

	public void addTenantNetwork(ITenantNetworkOperator tenantNetwork) {
		this.tenantNetworks.add(tenantNetwork);
		// TODO relate the bridges
	}

	public void removeTenantNetwork(ITenantNetworkOperator tenantNetwork) {
		this.tenantNetworks.remove(tenantNetwork);
		// TODO relate the bridges
	}

	public ITenantNetworkOperator getTenantNetwork(Tenant tenant) {
		for (ITenantNetworkOperator tenantNetwork : this.tenantNetworks) {
			if (tenantNetwork.getTenant().equals(tenant)) {
				return tenantNetwork;
			}
		}
		return null;
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

	public IHandleResponse handleBridgeCreate(Node node, OvsdbBridgeAugmentation ovsdbBridgeAugmentation) {
		INodeOperator no = this.getParentNode(node.getNodeId());
		IBridgeOperator bo = new Bridge(no, node, ovsdbBridgeAugmentation);
		no.addBridge(bo);
		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}
	public IHandleResponse handleBridgeDelete(Node node, OvsdbBridgeAugmentation ovsdbBridgeAugmentation) {
		INodeOperator no = this.getParentNode(node.getNodeId());
		IBridgeOperator bo = new Bridge(no, node, ovsdbBridgeAugmentation);
		no.removeBridge(bo);
		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}
	public IHandleResponse handleBridgeUpdate(Node node, OvsdbBridgeAugmentation ovsdbBridgeAugmentation) {
		INodeOperator no = this.getParentNode(node.getNodeId());
		for (IBridgeOperator bo : no.getBridges()) {
			if (bo.getNodeId().equals(node.getNodeId())) {
				bo.update(node, ovsdbBridgeAugmentation);
				break;
			}
		}
		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}
	
	public IHandleResponse handleNodeConnect(Node node, OvsdbNodeAugmentation ovsdbNodeAugmentation) {
		INodeOperator no = new Datapath(this, node, ovsdbNodeAugmentation);
		this.addNode(no);
		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}
	
	public IHandleResponse handleNodeConnectionAttributeChange(Node node, OvsdbNodeAugmentation ovsdbNodeAugmentation) {
		INodeOperator no = this.getNode(node.getNodeId());
		no.update(node, ovsdbNodeAugmentation);
		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}
	
	public IHandleResponse handleNodeDisconnect(Node node, OvsdbNodeAugmentation ovsdbNodeAugmentation) {
		INodeOperator no = new Datapath(this, node, ovsdbNodeAugmentation);
		this.removeNode(no.getNodeId());
		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}

	public IHandleResponse handlePortCreate(Node node, TerminationPoint tp, OvsdbTerminationPointAugmentation ovsdbTerminationPointAugmentation) {
		INodeOperator no = this.getParentNode(node.getNodeId());

		IPortOperator port;

		if (no == null) {
			throw new IllegalStateException("node is null on port create");
		}

		IBridgeOperator bo = null;

		for (IBridgeOperator br : no.getBridges()) {
			if (br.getNodeId().equals(node.getNodeId())) {
				bo = br; break;
			}
		}

		// is it a neutron port
		port = SouthboundHelper.maybeCreateHostPort(this.neutronPortCache, bo, tp, ovsdbTerminationPointAugmentation);

		if (port == null) {
			logger.info("not a neutron port");
			port = SouthboundHelper.maybeCreateInternalPort(bo, tp, ovsdbTerminationPointAugmentation);
		}

		if (port == null) {
			logger.info("not an internal port");
			port = new LinkPort(bo, tp, ovsdbTerminationPointAugmentation);
		}

		bo.addPort(port);

		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}

	public IHandleResponse handlePortDelete(Node node, OvsdbTerminationPointAugmentation ovsdbTerminationPointAugmentation) {
		INodeOperator no = this.getParentNode(node.getNodeId());

		if (no == null) {
			throw new IllegalStateException("node is null on port delete");
		}
		IBridgeOperator bo = null;

		for (IBridgeOperator br : no.getBridges()) {
			if (br.getNodeId().equals(node.getNodeId())) {
				bo = br; break;
			}
		}
		IPortOperator po = bo.getPort(ovsdbTerminationPointAugmentation.getPortUuid());

		bo.removePort(po);

		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}
	
	public IHandleResponse handlePortUpdate(Node node, TerminationPoint tp, OvsdbTerminationPointAugmentation ovsdbTerminationPointAugmentation) {
		INodeOperator no = this.getParentNode(node.getNodeId());

		if (no == null) {
			throw new IllegalStateException("node is null on port update");
		}

		IBridgeOperator bo = null;

		for (IBridgeOperator br : no.getBridges()) {
			if (br.getNodeId().equals(node.getNodeId())) {
				bo = br; break;
			}
		}

		IPortOperator po = bo.getPort(ovsdbTerminationPointAugmentation.getPortUuid());

		po.update(tp, ovsdbTerminationPointAugmentation);

		return new IHandleResponse() {
			public HandleStatus getStatus() {
				return HandleStatus.OK;
			}
		};
	}

    /**
     * Services provide this interface method for taking action after a port has been created
     *
     * @param port
     *            instance of new Neutron Port object
     */
    public void neutronPortCreated(NeutronPort port) {
    	IHostPort po = new HostPort(port);
    	this.neutronPortCache.add(po);
    }

    /**
     * Services provide this interface method for taking action after a port has been updated
     *
     * @param port
     *            instance of modified Neutron Port object
     */
    public void neutronPortUpdated(NeutronPort port) {
    	IHostPort cachedPort = this.getHostPortByNeutronId(port.getPortUUID());
    	cachedPort.update(port);
    }

    /**
     * Services provide this interface method for taking action after a port has been deleted
     *
     * @param port
     *            instance of deleted Port Network object
     */
    public void neutronPortDeleted(NeutronPort port) {
    	IHostPort cachedPort = this.getHostPortByNeutronId(port.getPortUUID());
    	this.neutronPortCache.remove(cachedPort);
    	for (IBridgeOperator bo : this.getBridges()) {
    		IHostPort po = bo.getHostPort(port.getPortUUID());
    		if (po != null) {
    			bo.removeHostPort(po);
    			return;
    		}
    	}
    }

    private IHostPort getHostPortByNeutronId(String id) {
    	for (IHostPort cachedPort : this.neutronPortCache) {
    		if (cachedPort.getNeutronUuid().equals(id)) {
    			return cachedPort;
    		}
    	}
    	return null;
    }
	
}