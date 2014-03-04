import java.util.Random;

import javax.measure.Measure;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.Simulator;
import rinde.sim.core.TickListener;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.road.MovingRoadUser;
import rinde.sim.core.model.road.PlaneRoadModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.PlaneRoadModelRenderer;
import rinde.sim.ui.renderers.RoadUserRenderer;

/**
 * This is a very simple example of the RinSim simulator that shows how a
 * simulation is set up. It is heavily documented to provide a sort of
 * 'walk-through' experience for new users of the simulator.<br/>
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class SimpleExperiment {

    // speed in km/h
    static final double VEHICLE_SPEED = 50d;

    private SimpleExperiment() {
    }

    /**
     * Starts the example.
     * 
     * @param args
     *            This is ignored.
     */
    public static void main(String[] args) {
        run(false);
    }

    public static void run(boolean testing) {
        // initialize a random generator which we use throughout this
        // 'experiment'
        final RandomGenerator rnd = new MersenneTwister(123);

        // initialize a new Simulator instance
        final Simulator sim = new Simulator(rnd, Measure.valueOf(1000L,
                SI.MILLI(SI.SECOND)));

        // register a PlaneRoadModel, a model which facilitates the moving of
        // RoadUsers on a plane. The plane is bounded by two corner points:
        // (0,0) and (10,10)
        sim.register(new PlaneRoadModel(new Point(0, 0), new Point(10, 10),
                SI.KILOMETER, Measure.valueOf(VEHICLE_SPEED,
                        NonSI.KILOMETERS_PER_HOUR)));
        // configure the simulator, once configured we can no longer change the
        // configuration (i.e. add new models) but we can start adding objects
        sim.configure();

        // add a number of drivers on the road
        final int numDrivers = 200;
        for (int i = 0; i < numDrivers; i++) {
            // when an object is registered in the simulator it gets
            // automatically 'hooked up' with models that it's interested in. An
            // object declares to be interested in an model by implementing an
            // interface.
            sim.register(new Driver(rnd));
        }
        // initialize the GUI. We use separate renderers for the road model and
        // for the drivers. By default the road model is rendererd as a square
        // (indicating its boundaries), and the drivers are rendererd as red
        // dots.
        final View.Builder viewBuilder = View.create(sim).with(
                new PlaneRoadModelRenderer(), new RoadUserRenderer());

        if (testing) {
            viewBuilder.setSpeedUp(16).enableAutoClose().enableAutoPlay()
                    .stopSimulatorAtTime(10 * 60 * 1000);
        }

        viewBuilder.show();
        // in case a GUI is not desired, the simulation can simply be run by
        // calling the start method of the simulator.
    }

    static class Driver implements MovingRoadUser, TickListener {
        // the MovingRoadUser interface indicates that this class can move on a
        // RoadModel. The TickListener interface indicates that this class wants
        // to keep track of time.

        protected RoadModel roadModel;
        protected final RandomGenerator rnd;
        protected Point destination;

        Driver(RandomGenerator r) {
            // we store the reference to the random generator
            rnd = r;
        }

        @Override
        public void initRoadUser(RoadModel model) {
            // this is where we receive an instance to the model. we store the
            // reference and add ourselves to the model on a random position.
            roadModel = model;
            roadModel.addObjectAt(this, roadModel.getRandomPosition(rnd));
            Point randomDestination = roadModel.getRandomPosition(rnd);
            Random rg = new Random();
            if (rg.nextBoolean()) {

                if (rg.nextBoolean()) {

                } else {

                }
            } else {

                if (rg.nextBoolean()) {

                } else {

                }
            }

        }

        @Override
        public void tick(TimeLapse timeLapse) {
            // every time step (tick) this gets called. Each time we chose a
            // different destination and move in that direction using the time
            // that was made available to us.
            Point randomPosition = roadModel.getRandomPosition(rnd);

            roadModel.moveTo(this, destination, timeLapse);
        }

        // private enum Side {
        // UL(2, 3, 7, 8), UR(7, 8, 7, 8), DL(2, 3, 2, 3), DR(7, 8, 2, 3), Up(
        // 3, 7, 7, 8), Down(3, 7, 2, 3), Left(2, 3, 3, 7), Right(7,
        // 8, 3, 7), Inside(3, 7, 3, 7), Outside(0, 10, 0, 10);
        //
        // private final double up;
        // private final double down;
        // private final double left;
        // private final double right;
        // private final Random rg = new Random(123);
        //
        // Side(double left, double right, double down, double up) {
        // this.up = up;
        // this.down = down;
        // this.left = left;
        // this.right = right;
        // }
        //
        // private Side insquare(Point pos) {
        // for (Side side : Side.values()) {
        // if (side.contains(pos))
        // return side;
        // }
        // }
        //
        // public boolean contains(Point pos) {
        // return pos.x >= left && pos.x <= right && pos.y >= down
        // && pos.y <= up;
        // }
        //
        // public Point transform(Point pos) {
        // double x;
        // double y;
        // switch(insquare(pos)) {
        // case UL: if(rg.nextBoolean()){ x = 2+0.6*pos.x;
        // y =}
        // }
        // }
        // }

        @Override
        public void afterTick(TimeLapse timeLapse) {
            // we don't need this in this example. This method is called after
            // all TickListener#tick() calls, hence the name.
        }

        @Override
        public double getSpeed() {
            // the drivers speed
            return VEHICLE_SPEED;
        }
    }
}