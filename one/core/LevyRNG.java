package core;

import java.util.Random;

public class LevyRNG {
	private Random rng;
	private double c;
	private double alpha;
	
	public LevyRNG(Random r, double c, double alpha) {
		this.rng = r;
		this.c = c;
		this.alpha = alpha;
	}

	public double getDouble() {
        double u, v, t, s;
        
        
        u = Math.PI * (rng.nextDouble()-0.5);
       
        if(alpha == 1)                          //CAUCHY
        {
                t = Math.tan(u);
               
                return c*t;
        }
       
        do
        {
                v = -Math.log(rng.nextDouble());
        } while(v == 0);
       
        if(alpha == 2)                                  //GAUSSIAN
        {
                t = 2*Math.sin(u)*Math.sqrt(v);
               
                return c*t;
        }
       
        //GENERAL CASE
        t = Math.sin(alpha*u) / Math.pow(Math.cos(u), 1/alpha);
        s = Math.pow(Math.cos((1-alpha)*u) / v, (1-alpha)/alpha);
       
        return c*t*s;
	}
}
