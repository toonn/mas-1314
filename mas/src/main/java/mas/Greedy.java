package mas;

import static com.google.common.collect.Maps.newHashMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.Simulator;
import rinde.sim.core.TickListener;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Graph;
import rinde.sim.core.graph.MultiAttributeData;
import rinde.sim.core.model.road.MovingRoadUser;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.examples.pdptw.gradientfield.GFParcel;
import rinde.sim.examples.pdptw.gradientfield.GradientFieldConfiguration;
import rinde.sim.examples.pdptw.gradientfield.GradientFieldExample;
import rinde.sim.examples.pdptw.gradientfield.GradientFieldRenderer;
import rinde.sim.pdptw.common.DefaultDepot;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.RouteRenderer;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.scenario.ScenarioController.UICreator;
import rinde.sim.serializers.DotGraphSerializer;
import rinde.sim.serializers.SelfCycleFilter;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.PDPModelRenderer;
import rinde.sim.ui.renderers.PlaneRoadModelRenderer;
import rinde.sim.ui.renderers.RoadUserRenderer;
import rinde.sim.ui.renderers.UiSchema;

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
public class Greedy {

	// speed in km/h
	static final double VEHICLE_SPEED = 5000 * 1000 / 3600;
	private static final String MAP_FILE = "/data/maps/leuven-simple.dot";
	private static final Map<String, Graph<MultiAttributeData>> GRAPH_CACHE = newHashMap();

	private Greedy() {
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

	public static void run(final boolean testing) {
		final UICreator uic = new UICreator() {
			@Override
			public void createUI(Simulator sim) {
				final UiSchema schema = new UiSchema(false);
				schema.add(GreedyVehicle.class, "/graphics/perspective/semi-truck-32.png");
				schema.add(DefaultDepot.class,
						"/graphics/flat/warehouse-32.png");
				schema.add(DefaultParcel.class, "/graphics/perspective/deliverypackage.png");
				final View.Builder viewBuilder = View.create(sim).with(
						new PlaneRoadModelRenderer(),
						new RoadUserRenderer(schema, false),
						new RouteRenderer(),
						new PDPModelRenderer(false));
				if (testing) {
					viewBuilder.enableAutoClose().enableAutoPlay()
							.setSpeedUp(64).stopSimulatorAtTime(60 * 60 * 1000);
				}
				viewBuilder.show();
			}
		};

		final Gendreau06Scenario scenario = Gendreau06Parser
				.parser()
				.addFile(
						GradientFieldExample.class
								.getResourceAsStream("/mas/gendreau06/req_rapide_1_450_24"),
						"req_rapide_1_450_24").allowDiversion().parse().get(0);

		final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
		Experiment.build(objFunc).withRandomSeed(123)
				.addConfiguration(new Configuration(false))
				.addScenario(scenario).showGui(uic).repeat(1).perform();
	}

	static class Driver implements MovingRoadUser, TickListener {
		// the MovingRoadUser interface indicates that this class can move on a
		// RoadModel. The TickListener interface indicates that this class wants
		// to keep track of time.

		protected RoadModel roadModel;
		protected final RandomGenerator rnd;

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
		}

		@Override
		public void tick(TimeLapse timeLapse) {
			// every time step (tick) this gets called. Each time we chose a
			// different destination and move in that direction using the time
			// that was made available to us.
			roadModel.moveTo(
					this,
					roadModel.getPosition(roadModel.getObjects().iterator()
							.next()), timeLapse);
		}

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

	// load the graph file
	static Graph<MultiAttributeData> loadGraph(String name) {
		try {
			if (GRAPH_CACHE.containsKey(name)) {
				return GRAPH_CACHE.get(name);
			}
			final Graph<MultiAttributeData> g = DotGraphSerializer
					.getMultiAttributeGraphSerializer(new SelfCycleFilter())
					.read(Greedy.class.getResourceAsStream(name));

			GRAPH_CACHE.put(name, g);
			return g;
		} catch (final FileNotFoundException e) {
			throw new IllegalStateException(e);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}
}