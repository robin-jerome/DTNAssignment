/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.Tuple;

/**
 * Implementation of CityRouter
 */
public class CityRouter extends ActiveRouter {
	
	/** City router's setting namespace ({@value})*/ 
	public static final String CITYROUTER_NS = "CityRouter";
	/** identifier for the low bufffer factor ({@value})*/ 
	public static final String LOW_BUFFER_FACTOR = "lowBufferFactor";
	/** identifier for the high bufffer factor ({@value})*/ 
	public static final String HIGH_BUFFER_FACTOR = "highBufferFactor";
	
	/** Nodes with 1 hop contact */
	private Map<DTNHost, Integer> firstHopStrata = new HashMap<DTNHost, Integer>();
	/** Nodes with 2 hop contact */
	private List<DTNHost> multiHopStrata = new LinkedList<DTNHost>();
	/** Data mule nodes */
	private List<DTNHost> dataMules = new LinkedList<DTNHost>();

	protected int lowBufferFactor;
	protected int highBufferFactor;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CityRouter(Settings s) {
		super(s);
		Settings cityRouterSettings = new Settings(CITYROUTER_NS);
		lowBufferFactor = cityRouterSettings.getInt(LOW_BUFFER_FACTOR);
		highBufferFactor = cityRouterSettings.getInt(HIGH_BUFFER_FACTOR);
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CityRouter(CityRouter r) {
		super(r);
		this.lowBufferFactor = r.lowBufferFactor;
		this.highBufferFactor = r.highBufferFactor;
	}
	
	/**
	 * Called when a connection is changed
	 * If the connection is up, the other node is stratified.
	 * 1. This node is added to the first hop strata of the current node.
	 * 2. All hosts in the stratas of this host are copied to the multihop strata of this host.
	 * 3. If this host is a data mule, then it is added to the list of data mules.
	 */
	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			if(isDataMule(otherHost) && !dataMules.contains(otherHost)){
				dataMules.add(otherHost);
			}
			addToFirstHopStrata(otherHost);
			addToMultiHopStrata(otherHost);
		}
	}

	/**
	 * Updates the list of first hop nodes with the newly found host.
	 * If the new host is already present in multihop strata it is 
	 * removed from there and added to the first hop strata. The count
	 * represents the number of times the host has been met 
	 * till the current time.
	 * @param host The host we just met
	 */
	private void addToFirstHopStrata(DTNHost host) {
		// If host present in second hop strata remove it.
		if(this.multiHopStrata.contains(host)){
			this.multiHopStrata.remove(host);
		}
		// If host is not present in the first hop strata, then add it.
		if(!this.firstHopStrata.containsKey(host)){
			this.firstHopStrata.put(host, 1);
		} else {
			int currCount = firstHopStrata.get(host);
			this.firstHopStrata.put(host, currCount+1);
		}
	}

	/**
	 * Updates the list of multi-hop nodes with the newly found host.
	 * All hosts in different stratas are added to the multihop strata 
	 * of the current host.
	 * @param host The host we just met
	 */
	private void addToMultiHopStrata(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof CityRouter : "CityRouter only works " + 
			" with other routers of same type";
		
		
		for(DTNHost dtnHost: ((CityRouter)otherRouter).getFirstHopStrata().keySet()){
			// Dont add yourself and also do not add elements already in the first hop strata
			if((dtnHost.getAddress()!= getHost().getAddress()) && !isNodePresentInAnyStrata(dtnHost)){
				this.multiHopStrata.add(dtnHost);
			}
		}
		
		for(DTNHost dtnHost: ((CityRouter)otherRouter).getMultiHopStrata()){
			// Dont add yourself and also do not add elements already in the first hop strata
			if((dtnHost.getAddress()!= getHost().getAddress()) && !isNodePresentInAnyStrata(dtnHost)){
				this.multiHopStrata.add(dtnHost);
			}
		}
		
		for(DTNHost dtnHost: ((CityRouter)otherRouter).getDataMules()){
			// Dont add yourself and also do not add elements already in the first hop strata
			if((dtnHost.getAddress()!= getHost().getAddress()) && !isNodePresentInAnyStrata(dtnHost)){
				this.multiHopStrata.add(dtnHost);
			}
		}
	}
	
	/**
	 * Helper method to check if there is a history of encounter with this node 
	 * @param host The host we just met
	 * @return boolean
	 */
	private boolean isNodePresentInAnyStrata(DTNHost host) {
		return (this.firstHopStrata.containsKey(host)||this.multiHopStrata.contains(host)||this.dataMules.contains(host));
	}
	
	/**
	 * Helper method to check if the host under context is a data mule.
	 * This algorithm assumes that mules have huge buffer sizes
	 * @param host The host we just met
	 * @return boolean
	 */
	private boolean isDataMule(DTNHost host) {
		return ((host.getRouter().getBufferSize() == 1000000000));
	}
	
	/**
	 *  Creates a new message.
	 */
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		addToMessages(msg, true);
		return true;
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		return msg;
	}

	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		this.trySendingMessages();
	}
	
	/**
	 * Tries to send messages to all connected hosts based on the type of
	 * node and the free available buffer size and the number of times this
	 * contact had been met before
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> trySendingMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		for (Connection con : getConnections()) {
			
			DTNHost other = con.getOtherNode(getHost());
			CityRouter othRouter = (CityRouter)other.getRouter();
			CityRouter selfRouter = (CityRouter)getHost().getRouter();
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : getMessageCollection()) {
			
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				/* Mules should have huge buffer, hence pass those messages whose destination nodes
				 * are in some strata of the peer node
				 */
				if(isDataMule(other)){
					if(othRouter.isNodePresentInAnyStrata(m.getTo())){
						messages.add(new Tuple<Message, Connection>(m,con));
					}
				}  
				else {
					
					/* Normal nodes do not have huge buffer, hence data needs to be passed selectively
					 * If the current node does not have the message's destination in any of its stratas,
					 * but the peer node has, then the message is always passed to the peer
					 */ 	
					if(!selfRouter.isNodePresentInAnyStrata(m.getTo()) && othRouter.isNodePresentInAnyStrata(m.getTo())){
						/* Seems this node has met the destination node but I haven't, Well I will try giving it to him */
						messages.add(new Tuple<Message, Connection>(m,con));
					} else if(selfRouter.getFirstHopStrata().containsKey(m.getTo()) && othRouter.getFirstHopStrata().containsKey(m.getTo())){
						/* If both the current node and its peer have met the destination of the message, the message is transferred
						 * only if
						 * 1. The peer node has met the destination more number of times than what the current node has
						 * 2. The peer host has high free buffer space and current host is running low on free buffer space 
						 */
						if(othRouter.getFirstHopStrata().get(m.getTo()) >= selfRouter.getFirstHopStrata().get(m.getTo())){
							/* Free Buffer size factor */
							if(othRouter.isRunningHighOnBuffer() && selfRouter.isRunningLowOnBuffer()) {
								messages.add(new Tuple<Message, Connection>(m,con));
							}
						}
					} else if(othRouter.getFirstHopStrata().containsKey(m.getTo()) && selfRouter.getMultiHopStrata().contains(m.getTo())){
						/* For the current node, the destination is multiple hops but for its peer the destination is singe hop.
						 * The message is transferred
						 * only if
						 * 1. The peer host has high free buffer space and current host is running low on free buffer space 
						 */
						if(othRouter.isRunningHighOnBuffer() && selfRouter.isRunningLowOnBuffer()) {
							messages.add(new Tuple<Message, Connection>(m,con));
						}
					}
				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	@Override
	public MessageRouter replicate() {
		CityRouter r = new CityRouter(this);
		return r;
	}

	public boolean isRunningLowOnBuffer() {
		return (getBufferSize()/getFreeBufferSize() > lowBufferFactor);
	}
	
	public boolean isRunningHighOnBuffer() {
		return (getBufferSize()/getFreeBufferSize() < highBufferFactor);
	}
	
	public Map<DTNHost, Integer> getFirstHopStrata() {
		return this.firstHopStrata;
	}

	public List<DTNHost> getMultiHopStrata() {
		return this.multiHopStrata;
	}
	
	public List<DTNHost> getDataMules() {
		return this.dataMules;
	}

	
}
