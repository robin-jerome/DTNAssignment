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
	
	private static double alpha = 0.34;
	private static double meanSpeed = 1.0;
	private static double speedVariance = 0.5;
	private static int timeInterval = 10;
	private static double phaseVariance = 1.0;
	private double meanDirection = rng.nextDouble()*2*Math.PI;
	
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
		
		sN = Double.NaN;
		dN = Double.NaN;
		
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
		this.meanDirection = gmm.meanDirection;
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
		if(Double.isNaN(this.sN)) {
			this.sN = meanSpeed;
		} 
		if(Double.isNaN(this.dN)) {
			this.dN = rng.nextDouble()*2*Math.PI;
		}
		
		double currSpeed, currDirection;
		double newX = 0, newY = 0, currX, currY;
		Path p = new Path();
		
		currSpeed = this.sN;
		currDirection = this.dN;
		currX = lastWaypoint.getX();
		currY = lastWaypoint.getY();
		
	if(currX > 200 && currX < 1800 && currY < 200){
			// Bottom Center
			this.meanDirection = Math.PI / 2;
		} else if(currX < 200 && currY < 200){
			// bottom left
			this.meanDirection = Math.PI / 4;
		} else if(currX < 200 && currY > 200 && currY < 1800){
			// center left
			this.meanDirection = 0;
		} else if(currX < 200 && currY > 1800){
			// Top left
			this.meanDirection = 7 * Math.PI / 4;
		} else if(currX > 200 && currX < 1800 && currY > 1800){
			// top center
			this.meanDirection = 3 * Math.PI / 2;
		} else if(currX > 1800 && currY > 1800){
			// top right
			this.meanDirection = 5 * Math.PI / 4;
		} else if(currX > 1800 && currY > 200 && currY < 1800){
			// center right
			this.meanDirection = Math.PI;
		} else if(currX > 1800 && currY < 200){
			// bottom right
			this.meanDirection = 3 * Math.PI / 4;
		}

		
		this.sN = generateSpeed(currSpeed);
		this.dN = generateDirection(currDirection);
		
		newX = currX + currSpeed*Math.cos(currDirection);
		newY = currY + currSpeed*Math.sin(currDirection);
		
		
		Coord newCord = new Coord(newX,newY);
		p.addWaypoint(newCord, currSpeed);
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
	
	protected double generateSpeed(double previousSpeed) {
		/* draw sample from Gaussian distribution and calculate speed accoring to Gauss-Markov equation */
		double oneMinusAlpha = 1d-alpha;
		double sqrtOneMinusAlphaSquare = Math.sqrt(1d - alpha*alpha);
		return((alpha*previousSpeed) + (oneMinusAlpha*meanSpeed) + Math.sqrt(speedVariance)*sqrtOneMinusAlphaSquare*speedGaussianRNG.nextGaussian());
		
		
	}
	
	protected double generateDirection(double previousDirection) {
		/* draw sample from Gaussian distribution and calculate direction accoring to Gauss-Markov equation */
		double oneMinusAlpha = 1d-alpha;
		double sqrtOneMinusAlphaSquare = Math.sqrt(1d - alpha*alpha);
		return((alpha*previousDirection) + (oneMinusAlpha*meanDirection) + Math.sqrt(phaseVariance)*sqrtOneMinusAlphaSquare*directionGaussianRNG.nextGaussian());
		
	}
	
	protected double getGaussianSample(Random rng, double variance) {
		return rng.nextGaussian() * variance;
	}
	
	protected Coord randomCoord() {
		return new Coord(rng.nextDouble() * getMaxX(),
				rng.nextDouble() * getMaxY());
	}
	
}
