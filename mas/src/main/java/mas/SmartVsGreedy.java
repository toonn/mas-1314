package mas;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.swt.graphics.RGB;

import rinde.sim.core.Simulator;
import rinde.sim.core.model.road.RoadModel;
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
				schema.add(SmartVehicle.class,
						"/graphics/perspective/semi-truck-32.png");
				schema.add(DefaultDepot.class,
						"/graphics/flat/warehouse-32.png");
				schema.add(DefaultParcel.class,
						"/graphics/perspective/deliverypackage.png");
				final UiSchema schema2 = new UiSchema(false);
				schema2.add(SmartVehicle.C_VERMILLION, new RGB(227, 66, 52));
				schema2.add(SmartVehicle.C_PERIWINKLE, new RGB(204, 204, 255));
				schema2.add(SmartVehicle.C_MALACHITE, new RGB(11, 218, 81));
				final View.Builder viewBuilder = View.create(sim).with(
						new PlaneRoadModelRenderer(),
						new RoadUserRenderer(schema, false),
						new RouteRenderer(),
						new PDPModelRenderer(false),
						new MessagingLayerRenderer(sim.getModelProvider()
								.getModel(RoadModel.class), schema2));
				if (testing) {
					viewBuilder.enableAutoClose().enableAutoPlay()
							.setSpeedUp(128)
							.stopSimulatorAtTime(100 * 60 * 60 * 1000);
				}
				viewBuilder.show();
			}
		};

		final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();

		List<String> gendreauResources = new LinkedList<String>();
		int[] nr = { 1, 2, 3, 4, 5 };
		int[] lod = { 240, 240, 450 };
		int[] freq = { 24, 33, 24 };
		for (int i : nr) {
			for (int j = 0; j < 3; j++) {
				gendreauResources.add("req_rapide_" + i + "_" + lod[j] + "_"
						+ freq[j]);
			}
		}

		for (String resource : gendreauResources) {
			final Gendreau06Scenario scenario = Gendreau06Parser
					.parser()
					.addFile(
							SmartVsGreedy.class.getResourceAsStream("/"
									+ resource), resource).allowDiversion()
					.parse().get(0);

			for (SimulationResult result : Experiment.build(objFunc)
					.withRandomSeed(123)
					.addConfiguration(new Configuration(true))
					.addScenario(scenario).showGui(uic).repeat(3).perform().results) {
				System.out.println(result.stats);
				//TODO statistieken
			}
		}
	}
}