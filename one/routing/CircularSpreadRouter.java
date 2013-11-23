package routing;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;

public class CircularSpreadRouter extends ActiveRouter {
	
	
	public CircularSpreadRouter(Settings s) {
		super(s);
	}
	
	public CircularSpreadRouter(CircularSpreadRouter r) {
		super(r);
	}
	
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
		
		// then try any/all message to any/all connection
		this.trySpreadingMessagesInConnections();
	}
	
	
	protected Connection trySpreadingMessagesInConnections(){
		List<Connection> connections = getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> messages = 
			new ArrayList<Message>(this.getMessageCollection());
		this.sortByQueueMode(messages);
		
		List<Connection> spreadingConnections = filterConnections(connections, connections.size());
		
		return tryMessagesToConnections(messages, spreadingConnections);
	}
		
	
	private List<Connection> filterConnections(List<Connection> connections, int requiredConnections){
		double selfDir = getDirectionofHost(getHost());
		List<Connection> filteredConnections = new LinkedList<Connection>();
		Map<Double, Connection> directionConnectionMap = new TreeMap<Double, Connection>();
		
		for(Connection conn : connections){
			double peerDir = getDirectionofHost(conn.getOtherNode(getHost()));
			double directionDeviation = Double.valueOf(Math.abs(selfDir-peerDir));
			directionConnectionMap.put(directionDeviation, conn);
		}
		
		int interval = connections.size()/requiredConnections;
		int count = 0;
		for(Connection conn : directionConnectionMap.values()) {
			count ++;
			if(count%interval == 0){
				filteredConnections.add(conn);
			}
		}
		
		if(filteredConnections.isEmpty() && !connections.isEmpty()){
			return connections;
		} else {
			// return a subset of collections
			return filteredConnections;
		}
		
		
	}
	
	private double getDirectionofHost(DTNHost host) {
		double nodeDir = 0d;
		if(null != host.getPath()) {
			Coord selfLoc = host.getLocation();
			Coord nextLoc = host.getPath().hasNext() == true ? host.getPath().getNextWaypoint() : null;
			if(null != nextLoc){
				nodeDir =  getDirection(selfLoc, nextLoc);
			}
		}
		return nodeDir;
	}

	private double getDirection(Coord selfLoc, Coord nextLoc) {
		if(nextLoc.getX() != selfLoc.getX()){
			return((nextLoc.getY()-selfLoc.getY())/(nextLoc.getX()-selfLoc.getX()));
		} else if(nextLoc.getY() > selfLoc.getY()){
			return 1d;
		} else if(nextLoc.getY() < selfLoc.getY()){
			return -1d;
		} else {
			return 0;
		}
	}

	@Override
	public CircularSpreadRouter replicate() {
		return new CircularSpreadRouter(this);
	}

}
