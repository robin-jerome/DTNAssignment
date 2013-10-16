package movement;

import java.util.Random;
import core.Coord;
import core.Settings;
import core.SimClock;

public class GaussMarkovModel extends MovementModel {
	private static final int PATH_LENGTH = 1;
	private static final int EDGE_DISTANCE = 50;
	
	public static final String ALPHA = "alpha";
	public static final String MEANSPEED = "meanSpeed";
	public static final String SPEEDVARIANCE = "speedVariance";
	public static final String PHASEVARIANCE = "phaseVariance";
	public static final String SPEED_GAUSS_SEED = "speedGaussMarkovRNGSeed";
	public static final String PHASE_GAUSS_SEED = "phaseGaussMarkovRNGSeed";
	public static final String TIMEINTERVAL = "timeInterval";
	
	private static final String GAUSS_MARKOV_NS = "GaussMarkovModel";
	
	private Coord lastWaypoint;
	
	private double sN;
	private double dN;
	
	private Random speedGaussianRNG;
	private Random directionGaussianRNG;
	
	private static double alpha = 0.5;
	private static double meanSpeed = 2.0;
	private static double speedVariance = 0.5;
	private static int timeInterval = 10;
	private static double phaseVariance = 1.0;
	private double meanDirection;
	
	public GaussMarkovModel(Settings s) {
		super(s);
		Settings settings = new Settings(GAUSS_MARKOV_NS);
		if(settings.contains(ALPHA))
			GaussMarkovModel.alpha = settings.getDouble(ALPHA);
		if(settings.contains(MEANSPEED))
			GaussMarkovModel.meanSpeed = settings.getDouble(MEANSPEED);
		if(settings.contains(SPEEDVARIANCE))
			GaussMarkovModel.speedVariance = settings.getDouble(SPEEDVARIANCE);
		if(settings.contains(PHASEVARIANCE))
			GaussMarkovModel.phaseVariance = settings.getDouble(PHASEVARIANCE);
		if(settings.contains(TIMEINTERVAL))
			GaussMarkovModel.timeInterval = settings.getInt(TIMEINTERVAL);
		
		this.sN = Double.NaN;
		this.dN = Double.NaN;
		this.meanDirection = rng.nextDouble()*2*Math.PI;
		
		if(settings.contains(SPEED_GAUSS_SEED))
			this.speedGaussianRNG = new Random(settings.getInt(SPEED_GAUSS_SEED));
		else
			this.speedGaussianRNG = new Random(0);
		
		if(settings.contains(PHASE_GAUSS_SEED))
			this.directionGaussianRNG = new Random(settings.getInt(PHASE_GAUSS_SEED));
		else
			this.directionGaussianRNG = new Random(0);
	}
	
	private GaussMarkovModel(GaussMarkovModel gmm) {
		super(gmm);
		this.speedGaussianRNG = gmm.speedGaussianRNG;
		this.directionGaussianRNG = gmm.directionGaussianRNG;
		this.sN = Double.NaN;
		this.dN = Double.NaN;
	}
	
	@Override
	public Path getPath() {
		/* 1. If speed not initialised set it to the mean value
		   2. If direction not initialised, pick random direction (uniformly distributed between 0 and 2pi)
		   3. in the loop (its length is based on PATH_LENGTH value) do: 
		      - calculate s_n and d_n 
			  - calculate coordinate of the new node's position by moving it from current location for fixed time interval
				with calculated speed s_n in direction d_n
			  - add a new waypoint to the path with (x_n, y_n) and speed s_n 
		   4. store final value of s_n and d_n 
		   5. return full path */
		if(Double.isNaN(sN)) {
			sN = meanSpeed;
		} 
		if(Double.isNaN(dN)) {
			dN = rng.nextDouble()*2*Math.PI;
		}
		
		
		Path p = new Path();
		
		double xnminusone = lastWaypoint.getX();
		double ynminusone = lastWaypoint.getY();
		
		if(xnminusone > 100 && xnminusone < 1900 && ynminusone < 100){
			// Point near the roof -> change the mean direction to 270 degrees
		} else if(xnminusone < 100 && ynminusone < 100){
			// Point near the top left corner -> change the mean direction to 315 degrees
		} else if(xnminusone < 100 && ynminusone > 100 && ynminusone < 1900){
			// Point near the left border -> change the mean direction to 0 degrees
		} else if(xnminusone < 100 && ynminusone > 1900){
			// Point near the bottom left corner -> change the mean direction to 45 degrees
		} else if(xnminusone > 100 && xnminusone < 1900 && ynminusone > 1900){
			// Point near the bottom exit -> change the mean direction to 90 degrees
		} else if(xnminusone > 1900 && ynminusone > 1900){
			// Point near the bottom right corner -> change the mean direction to 135 degrees
		} else if(xnminusone > 1900 && ynminusone > 100 && ynminusone < 1900){
			// Point near the right border -> change the mean direction to 180 degrees
		} else if(xnminusone > 1900 && ynminusone < 100){
			// Point near the top right corner -> change the mean direction to 225 degrees
		}
		
		sN = generateSpeed();
		dN = generateDirection();
		double xn = xnminusone + sN*Math.cos(2*Math.PI*dN);
		double yn = ynminusone + sN*Math.sin(2*Math.PI*dN);
		Coord newCord = new Coord(xn,yn);
		p.addWaypoint(newCord, sN);
		this.lastWaypoint = newCord;
		return p;
		
		
	}

	@Override
	public Coord getInitialLocation() {
		/* pick random location for the initial positioning (look and RWP code if you need help) */
		assert rng != null : "MovementModel not initialized!";
		Coord c = randomCoord();

		this.lastWaypoint = c;
		return c;
	}
	
	protected double generateWaitTime() {
		/* there are no pauses */
		return 0;
	}

	@Override
	public MovementModel replicate() {
		return new GaussMarkovModel(this);
	}
	
	protected double generateSpeed() {
		/* draw sample from Gaussian distribution and calculate speed accoring to Gauss-Markov equation */
		double oneMinusAlpha = 1-alpha;
		double sqrtOneMinusAlphaSquare = Math.sqrt(1- alpha*alpha);
		sN = (alpha*sN) + (oneMinusAlpha*meanSpeed) + Math.sqrt(speedVariance)*sqrtOneMinusAlphaSquare*speedGaussianRNG.nextDouble();
		return sN;
	}
	
	protected double generateDirection() {
		/* draw sample from Gaussian distribution and calculate direction accoring to Gauss-Markov equation */
		double oneMinusAlpha = 1-alpha;
		double sqrtOneMinusAlphaSquare = Math.sqrt(1- alpha*alpha);
		dN = (alpha*dN) + (oneMinusAlpha*meanDirection) + Math.sqrt(phaseVariance)*sqrtOneMinusAlphaSquare*directionGaussianRNG.nextDouble();
		return dN;
	}
	
	protected double getGaussianSample(Random rng, double variance) {
		return rng.nextGaussian() * variance;
	}
	
	protected Coord randomCoord() {
		return new Coord(rng.nextDouble() * getMaxX(),
				rng.nextDouble() * getMaxY());
	}
	
}
