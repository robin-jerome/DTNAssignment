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
public class CityRouterAlternative extends ActiveRouter {
	
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
	private List<DTNHost> secondHopStrata = new LinkedList<DTNHost>();
	/** Coefficient for the buffer size variation*/
	protected int bufferSizeCoeff;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CityRouterAlternative(Settings s) {
		super(s);
		Settings cityRouterSettings = new Settings(CITYROUTER_NS);
		initialNrofCopies = cityRouterSettings.getInt(NROF_COPIES);
		isBinary = cityRouterSettings.getBoolean(BINARY_MODE);
		bufferSizeCoeff = cityRouterSettings.getInt(BUFFER_SIZE_COEFFICIENT);
		initStratas();
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CityRouterAlternative(CityRouterAlternative r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.bufferSizeCoeff = r.bufferSizeCoeff;
		initStratas();
	}
	
	/**
	 * Initializes predictability hash
	 */
	private void initStratas() {
		// Do nothing
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			addToFirstHopStrata(otherHost);
			addToSecondHopStrata(otherHost);
		}
	}

	private void addToFirstHopStrata(DTNHost host) {

		// If host present in second hop strata remove it and add to first hop strata
		if(this.secondHopStrata.contains(host)){
			this.secondHopStrata.remove(host);
		}
		this.firstHopStrata.add(host);
	}

	private void addToSecondHopStrata(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof CityRouterAlternative : "CityRouter only works " + 
			" with other routers of same type";
		
		for(DTNHost h: ((CityRouterAlternative)otherRouter).getFirstHopStrata()){
			// Dont add yourself and also do not add elements already in the first hop strata
			if((h.getAddress()!= getHost().getAddress()) && !this.firstHopStrata.contains(h)){
				this.secondHopStrata.add(h);
			}
		}
	}
	
	private boolean isNodePresentInAnyStrata(DTNHost host) {
		return (this.firstHopStrata.contains(host)||this.secondHopStrata.contains(host));
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

	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
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
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.trySendingMessages(copiesLeft);
		}
		
				
	}
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> trySendingMessages(List<Message> copiesLeft) {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = copiesLeft;
		
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			CityRouterAlternative othRouter = (CityRouterAlternative)other.getRouter();
			CityRouterAlternative selfRouter = (CityRouterAlternative)getHost().getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				/* Check if selfRouter or othRouter has huge Buffer Capacity. */ 
				 
				/* 1. If selfRouter has huge buffer and other router has low capacity,
				 * transfer only those packets which have destination nodes in first-hop
				 * or second-hop strata. 
				 * */
				// Representative of spreading packets from mule to a normal host
				if(selfRouter.getBufferSize() > (othRouter.getBufferSize()*bufferSizeCoeff)){
					if(isNodePresentInAnyStrata(m.getTo())){
						m.updateProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
						messages.add(new Tuple<Message, Connection>(m,con));
					}
				} else if(othRouter.getBufferSize() > (selfRouter.getBufferSize()*bufferSizeCoeff)){
				/* 2. If other router has huge buffer and self router has low buffer, transfer all packets
				 * whose destinations do not belong to the first hop strata. */
				// Representative of putting all packets in a mulSystem.out.println("Host & mule meet");
//					if(!this.firstHopStrata.contains(m.getTo())){
						m.updateProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
						messages.add(new Tuple<Message, Connection>(m,con));
//					}
				} else {
				/* 3. Data transfer could be between two normal hosts or between two mules.
				 * Use spray and wait algorithm here. */ 
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
		CityRouterAlternative r = new CityRouterAlternative(this);
		return r;
	}
	
//	public List<DTNHost> getFirstHopStrataClone() {
//		List<DTNHost> copiedList = this.firstHopStrata;
//		return copiedList;
//	}
//	
//	public List<DTNHost> getSecondHopStrataClone() {
//		List<DTNHost> copiedList = this.secondHopStrata;
//		return copiedList;
//	}

	public List<DTNHost> getFirstHopStrata() {
		return this.firstHopStrata;
	}

	public void setFirstHopStrata(List<DTNHost> firstHopStrata) {
		this.firstHopStrata = firstHopStrata;
	}

	public List<DTNHost> getSecondHopStrata() {
		return this.secondHopStrata;
	}

	public void setSecondHopStrata(List<DTNHost> secondHopStrata) {
		this.secondHopStrata = secondHopStrata;
	}

}
