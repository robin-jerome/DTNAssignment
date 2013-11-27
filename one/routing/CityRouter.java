/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BUFFER_SIZE_COEFFICIENT = "bufferSizeCoefficient";
	/** identifier for the buffer size coefficient setting ({@value})*/
	public static final String BINARY_MODE = "binaryMode";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = CITYROUTER_NS + "." + "copies";
	/** Initial number of copies of message */
	protected int initialNrofCopies;
	/** Is binary mode of operation */
	protected boolean isBinary;
	/** Nodes with 1 hop contact */
	private List<DTNHost> firstHopStrata = new LinkedList<DTNHost>();
	/** Nodes with 2 hop contact */
	private List<DTNHost> multiHopStrata = new LinkedList<DTNHost>();
	/** Nodes with 2 hop contact */
	private List<DTNHost> dataMules = new LinkedList<DTNHost>();
	/** Coefficient for the buffer size variation*/
	protected int bufferSizeCoeff;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CityRouter(Settings s) {
		super(s);
		Settings cityRouterSettings = new Settings(CITYROUTER_NS);
		initialNrofCopies = cityRouterSettings.getInt(NROF_COPIES);
		isBinary = cityRouterSettings.getBoolean(BINARY_MODE);
		bufferSizeCoeff = cityRouterSettings.getInt(BUFFER_SIZE_COEFFICIENT);
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CityRouter(CityRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.bufferSizeCoeff = r.bufferSizeCoeff;
	}
	
	
	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			if(isDataMule(otherHost)){
				dataMules.add(otherHost);
			}
			addToFirstHopStrata(otherHost);
			addToMultiHopStrata(otherHost);
		}
	}

	private void addToFirstHopStrata(DTNHost host) {
		// If host present in second hop strata remove it.
		if(this.multiHopStrata.contains(host)){
			this.multiHopStrata.remove(host);
		}
		// If host is not present in the first hop strata, then add it.
		if(!this.firstHopStrata.contains(host)){
			this.firstHopStrata.add(host);
		}
	}

	private void addToMultiHopStrata(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof CityRouter : "CityRouter only works " + 
			" with other routers of same type";
		
		for(DTNHost dtnHost: ((CityRouter)otherRouter).getMultiHopStrata()){
			// Dont add yourself and also do not add elements already in the first hop strata
			if((dtnHost.getAddress()!= getHost().getAddress()) && !isNodePresentInAnyStrata(dtnHost)){
				this.multiHopStrata.add(dtnHost);
				if(isDataMule(dtnHost)){
					dataMules.add(dtnHost);
				}
			}
		}
	}
	
	private boolean isNodePresentInAnyStrata(DTNHost host) {
		return (this.firstHopStrata.contains(host)||this.multiHopStrata.contains(host)||this.dataMules.contains(host));
	}
	
	private boolean isDataMule(DTNHost host) {
		return ((host.getRouter().getBufferSize() == 1000000000));
	}
	
	
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		if (isBinary) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}

	protected List<Message> getMessagesWithCopiesLeft(boolean isMule) {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if(isMule){
				list.add(m);
			} else if (nrofCopies > 1) {
				list.add(m);
			}
		}
		return list;
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
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> trySendingMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		for (Connection con : getConnections()) {
			
			DTNHost other = con.getOtherNode(getHost());
			DTNHost self = getHost();
			CityRouter othRouter = (CityRouter)other.getRouter();
			CityRouter selfRouter = (CityRouter)getHost().getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			List<Message> msgCollection = getMessagesWithCopiesLeft(isDataMule(other));
			
			for (Message m : msgCollection) {
			
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				
				if(isDataMule(self) || isDataMule(other)){
					if(othRouter.isNodePresentInAnyStrata(m.getTo())){
						m.updateProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
						messages.add(new Tuple<Message, Connection>(m,con));
					}
				} else {
					/* 3. Data transfer could be between two normal hosts.
					 * Use spray and wait algorithm here. */ 
					
					// TO-DO: Add check for destination node of message in any strata
					
					Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
					assert nrofCopies != null : "Not a SnW message: " + m;

					if (isBinary) {
						/* in binary S'n'W the receiving node gets ceil(n/2) copies */
						nrofCopies = (int)Math.ceil(nrofCopies/2.0);
					}
					else {
						/* in standard S'n'W the receiving node gets only single copy */
						nrofCopies = 1;
					}
					m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
					messages.add(new Tuple<Message, Connection>(m,con));	
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

	public List<DTNHost> getFirstHopStrata() {
		return this.firstHopStrata;
	}

	public List<DTNHost> getMultiHopStrata() {
		return this.multiHopStrata;
	}

	
}
