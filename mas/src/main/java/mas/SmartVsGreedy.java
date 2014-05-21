package mas;

import rinde.sim.core.Simulator;
import rinde.sim.pdptw.common.DefaultDepot;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.RouteRenderer;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.scenario.ScenarioController.UICreator;
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
public class SmartVsGreedy {

	private SmartVsGreedy() {
	}

	/**
	 * Starts the example.
	 * 
	 * @param args
	 *            This is ignored.
	 */
	public static void main(String[] args) {
		run(true);
	}

	public static void run(final boolean testing) {
		final UICreator uic = new UICreator() {
			@Override
			public void createUI(Simulator sim) {
				final UiSchema schema = new UiSchema(false);
				schema.add(GreedyVehicle.class,
						"/graphics/perspective/semi-truck-32.png");
				schema.add(DefaultDepot.class,
						"/graphics/flat/warehouse-32.png");
				schema.add(DefaultParcel.class,
						"/graphics/perspective/deliverypackage.png");
				final View.Builder viewBuilder = View.create(sim).with(
						new PlaneRoadModelRenderer(),
						new RoadUserRenderer(schema, false),
						new RouteRenderer(), new PDPModelRenderer(false));
				if (testing) {
					viewBuilder.enableAutoClose().enableAutoPlay()
							.setSpeedUp(64)
							.stopSimulatorAtTime(10 * 60 * 60 * 1000);
				}
				viewBuilder.show();
			}
		};

		final Gendreau06Scenario scenario = Gendreau06Parser
				.parser()
				.addFile(
						SmartVsGreedy.class
								.getResourceAsStream("/req_rapide_1_240_24"),
						"req_rapide_1_240_24").allowDiversion().parse().get(0);
		final Gendreau06Scenario scenario2 = Gendreau06Parser
				.parser()
				.addFile(
						SmartVsGreedy.class
								.getResourceAsStream("/req_rapide_1_240_33"),
						"req_rapide_1_240_33").allowDiversion().parse().get(0);
		final Gendreau06Scenario scenario3 = Gendreau06Parser
				.parser()
				.addFile(
						SmartVsGreedy.class
								.getResourceAsStream("/req_rapide_1_450_24"),
						"req_rapide_1_450_24").allowDiversion().parse().get(0);

		final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();

		for (SimulationResult result : Experiment.build(objFunc)
				.withRandomSeed(123).addConfiguration(new Configuration(false))
				.addScenario(scenario).addScenario(scenario2).addScenario(scenario3).showGui(uic)
				.repeat(1).perform().results) {
			System.out.println(result.stats);
		}
	}
}