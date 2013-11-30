package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;

public class CircularSpreadRouter extends ActiveRouter {
	
	/** String identifier to determine the # of copies to begin the message transfer with ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** String identifier for determining the total number inter-cardinal directions ({@value})
	 *  The total cardinal direction is limited to a max of 8
	 * */ 
	public static final String DIRECTION_COEFF = "directionCoefficient";
	/** identifier for the binary-mode setting, similar to spray and wait ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** Circular spread router namespace ({@value})*/ 
	public static final String CIRCULARSPREAD_NS = "CircularSpreadRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = CIRCULARSPREAD_NS + "." +
		"copies";
	public static final String MSG_SENT_DIRECTIONS = "sentDirections";
	
	protected int initialNrofCopies;
	protected boolean isBinary;
	protected int directionCoefficient;
	
	// total cardinal directions supported for optimal performance
	public static enum Directions {
		DIR1(1), DIR2(2), DIR3(3), DIR4(4), DIR5(5), DIR6(6), DIR7(7), DIR8(8);
		
		int id;
		Directions(int id) {
		    this.id = id;
		}
		
	}
	
	public CircularSpreadRouter(Settings s) {
		super(s);
		Settings csnwSettings = new Settings(CIRCULARSPREAD_NS);
		initialNrofCopies = csnwSettings.getInt(NROF_COPIES);
		directionCoefficient = csnwSettings.getInt(DIRECTION_COEFF);
		isBinary = csnwSettings.getBoolean(BINARY_MODE);
	}
	
	public CircularSpreadRouter(CircularSpreadRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.directionCoefficient = r.directionCoefficient;
	}
	
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a CircularSpread message: " + msg;
		
		if (isBinary) {
			/* If binary mode is enabled, similar to binary S'n'W 
			 * the receiving node gets ceil(n/2) copies of the message
			 */
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		}
		else {
			/* If binary mode is false, then give only one copy to the next node*/
			nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}
	
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		msg.addProperty(MSG_SENT_DIRECTIONS, getEmptyDirectionHashMap());
		addToMessages(msg, true);
		return true;
	}
	

	private Map<Integer, Boolean> getEmptyDirectionHashMap() {
		Map<Integer, Boolean> map = new HashMap<Integer, Boolean>();
		map.put(Directions.DIR1.id, Boolean.FALSE);
		map.put(Directions.DIR2.id, Boolean.FALSE);
		map.put(Directions.DIR3.id, Boolean.FALSE);
		map.put(Directions.DIR4.id, Boolean.FALSE);
		map.put(Directions.DIR5.id, Boolean.FALSE);
		map.put(Directions.DIR6.id, Boolean.FALSE);
		map.put(Directions.DIR7.id, Boolean.FALSE);
		map.put(Directions.DIR8.id, Boolean.FALSE);
		return map;
	}

	/** 
	 * From the direction of travel of the node (in radians), it determines the
	 * cardinal direction of the node. The total number of cardinal directions are based on the
	 * direction coefficient
	 * @param radian
	 * @return returns the Direction ENUM
	 */
	private Directions getDirectionFromRadian(double radian){
		double cardinalDirectionsplits = 2 * Math.PI / this.directionCoefficient;
		int i;
		for (i = 1; i <= this.directionCoefficient; i++) {
			if (radian <= cardinalDirectionsplits * i) {
				break;
			}
		}
		switch (i) {
		case 0: return Directions.DIR1;
		case 1: return Directions.DIR2;
		case 2: return Directions.DIR3;
		case 3: return Directions.DIR4;
		case 4: return Directions.DIR5;
		case 5: return Directions.DIR6;
		case 6: return Directions.DIR7;
		case 7: return Directions.DIR8;
		default: return Directions.DIR8;
		}
	}

/*	
 * This function was an attempt to make the message see direction relative to host node.
 * But this totally digresses from the actual design of the alogoithm
 * 
	private Directions getDirectionFromRadian(double referenceRadian, double radian){
		double cardinalDirectionsplits = Math.PI / this.directionCoefficient;
		double deviation = 0, newRacelimit = 0;
		int i = 0, flag = 0;
		for (i = 0; i < this.directionCoefficient; i++) {
			deviation = Math.abs(referenceRadian - radian);
			if (referenceRadian > ((2 * Math.PI) - cardinalDirectionsplits)) {
				newRacelimit = cardinalDirectionsplits - (2 * Math.PI - referenceRadian);
				if (radian <= newRacelimit) {
					break;
				}
				flag = 1;
			}
			if (referenceRadian < cardinalDirectionsplits) {
				newRacelimit = (2 * Math.PI) - (cardinalDirectionsplits - referenceRadian);
				if (radian >= newRacelimit) {
					break;
				}
			}
			if (deviation <= cardinalDirectionsplits) {
					break;
			}
			if (flag == 1) {
				referenceRadian = newRacelimit + cardinalDirectionsplits;
				flag = 0;
			} else {
				referenceRadian += (2 * cardinalDirectionsplits);
			}
		}
		
		switch (i) {
		case 0: return Directions.DIR1;
		case 1: return Directions.DIR2;
		case 2: return Directions.DIR3;
		case 3: return Directions.DIR4;
		case 4: return Directions.DIR5;
		case 5: return Directions.DIR6;
		case 6: return Directions.DIR7;
		case 7: return Directions.DIR8;
		default: return Directions.DIR8;
		}
	}
	*/
	
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		Set<Directions> uniqueDirections = new HashSet<Directions>();
		List<Connection> connections = getConnections();
		for(Connection conn: connections){
			// Get the absolute direction of the host.
			uniqueDirections.add(getDirectionFromRadian(getDirectionofHost(conn.getOtherNode(getHost()))));
		}
		
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesToSpread = sortByQueueMode(getMessagesWithCopiesLeftNotTravelledInDirections(uniqueDirections));
		
		if (copiesToSpread.size() > 0) {
			trySpreadingMessagesInConnections(connections, copiesToSpread);
		}
	}
	
	
	protected Connection trySpreadingMessagesInConnections(List<Connection> connections, List<Message> copiesLeft){
		
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}
		// Now filter the connected nodes based on their direction relative to the actual host
		// Only Valid connected nodes are used for spreading the message.
		List<Connection> spreadingConnections = filterConnections(connections);
		return tryMessagesToConnections(copiesLeft, spreadingConnections);
		
	}
		
	/**
	 * "Every node sees relative cardinal direction"
	 * 
	 * This function forms one of the basis of the circular spread algorithm.
	 * Each connected node is a "valid" node only if : 
	 * 1. The cardinal direction of the node is relatively different from the 
	 *    direction of travel of the host it is connected to, (and) 
	 * 2. The speed of the connected node is greater than the current speed of
	 *    host.
	 * 
	 * @param connections
	 * @return List of filtered connections which are valid
	 */
	private List<Connection> filterConnections(List<Connection> connections){
		
		double selfDir = getDirectionofHost(getHost());
		List<Connection> filteredConnections = new LinkedList<Connection>();
		
		for(Connection conn : connections){
			double peerDir = getDirectionofHost(conn.getOtherNode(getHost()));
			if (peerDir == -1 || selfDir == -1)
				continue;
			double directionDeviation = Double.valueOf(Math.abs(selfDir-peerDir));
			
			if (directionDeviation > (Math.PI / directionCoefficient)) {
				 /* Passed message to node with different relative direction */
				filteredConnections.add(conn);
			} else if(getHost().getPath().getSpeed() < conn.getOtherNode(getHost()).getPath().getSpeed()) {
				/* Passed message to node with higher speed */
				filteredConnections.add(conn);
			} else {
				/* Message not passed to node */
			}
		}
		return filteredConnections;
		
	}
	
	/**
	 * Calculates the slope/direction (in radians) of the given host from the
	 * current and next location.
	 * @param host
	 * @return the direction in radians (or) -1 (in case of error)
	 */
	private double getDirectionofHost(DTNHost host) {
		if(null != host.getPath() && host.getPath().getCoords().size() == 2) {
			Coord selfLoc = host.getPath().getCoords().get(0);
			Coord nextLoc = host.getPath().getCoords().get(1);
			return getDirection(selfLoc, nextLoc);
		} else {
			return -1d;
		}
		
	}

	private double getDirection(Coord selfLoc, Coord nextLoc) {
		double rise = nextLoc.getY()-selfLoc.getY();
		double run = nextLoc.getX()-selfLoc.getX();
		double radian_direction = Math.atan2(rise, run);
		
		if (rise < 0) {
			return (2 * Math.PI + radian_direction);   // For any downward direction, the radian_direction is < 0,
		}											   // hence we add it to 360 to get mean direction in radian
		return (radian_direction);
	}

	@Override
	public CircularSpreadRouter replicate() {
		return new CircularSpreadRouter(this);
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
	
	/**
	 * "Every Message sees absolute cardinal direction"
	 * 
	 * This function lays the ground work for the second concept used in this
	 * algorithm. Every message has a direction property which allows the router
	 * to determine if the message has already traveled in a particular direction.
	 * If the message has already traveled in that direction then do not send the
	 * message, else send the message in that direction.
	 * @param directions
	 * @return List of Messages which can be send in that direction.
	 */
	protected List<Message> getMessagesWithCopiesLeftNotTravelledInDirections(Set<Directions> directions) {
		List<Message> list = new ArrayList<Message>();
		for (Message m : getMessagesWithCopiesLeft()) {
			Map<Integer, Boolean> messageDirections = (HashMap<Integer, Boolean>)m.getProperty(MSG_SENT_DIRECTIONS);
			for(Directions dir: directions){
				if(messageDirections.get(dir.id).booleanValue() == false){
					list.add(m);
//					System.out.println("Message has not been sent in this direction yet,Will be sent now: "+dir);
					break;
				} else {
//					System.out.println("Message already sent in this direction - Not sent again");
				}
			}
		}
		return list;
	}
	
	
	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary mode, the sending host is left with n/2 copies but 
	 * in standard mode, nr of copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) { 
			nrofCopies /= 2;
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		
		Map<Integer, Boolean> messageDirections = (HashMap<Integer, Boolean>)msg.getProperty(MSG_SENT_DIRECTIONS);
		// Determine the direction in which the host is traveling 
		Directions newDir = getDirectionFromRadian(getDirectionofHost(con.getOtherNode(getHost())));
		// Sent the current direction to TRUE, since the message 
		// has now been sent via this direction.
		messageDirections.put(newDir.id, Boolean.TRUE);
		msg.updateProperty(MSG_SENT_DIRECTIONS, messageDirections);
	}
	
}
