package mas;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.RGB;

import rinde.sim.core.Simulator;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DefaultDepot;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.RouteRenderer;
import rinde.sim.pdptw.common.StatisticsDTO;
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
		new SmartVsGreedy().run(true);
	}

	public void run(final boolean testing) {
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
							.setSpeedUp(64)
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

		Map<String, Map<String, ExperimentStats>> experimentStats = new HashMap<String, Map<String, ExperimentStats>>();
		for (String resource : gendreauResources) {
			Map<String, ExperimentStats> configStats = new HashMap<String, SmartVsGreedy.ExperimentStats>();
			final Gendreau06Scenario scenario = Gendreau06Parser
					.parser()
					.addFile(
							SmartVsGreedy.class.getResourceAsStream("/"
									+ resource), resource).allowDiversion()
					.parse().get(0);

			Configuration configuration = new Configuration(true);
			ExperimentStats expStats = new ExperimentStats(resource,
					configuration.toString());
			for (SimulationResult result : Experiment.build(objFunc)
					.withRandomSeed(123).addConfiguration(configuration)
					.addScenario(scenario)/*.showGui(uic)*/.repeat(3).perform().results) {

				expStats.addStats(result.stats);
			}
			configStats.put(configuration.toString(), expStats);
			experimentStats.put(resource, configStats);
		}

		String json = "{ ";
		for (String resource : experimentStats.keySet()) {
			json += "\"" + resource + "\" : { ";
			for (String configuration : experimentStats.get(resource).keySet()) {
				json += "\""
						+ configuration
						+ "\" : "
						+ experimentStats.get(resource).get(configuration)
								.toString() + ", ";
			}
			json = json.substring(0, json.length() - 2);
			json += "},\n";
		}
		json = json.substring(0, json.length() - 2);
		json += "\n}";

		System.out.println(json);
		writeTextFile("../smartvehicle_cost10_plan.json", json);
	}

	public void writeTextFile(String fileName, String s) {
		FileWriter output = null;
		try {
			output = new FileWriter(fileName);
			BufferedWriter writer = new BufferedWriter(output);
			writer.write(s);
			writer.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException e) {
					// Ignore issues during closing
				}
			}
		}

	}

	private class ExperimentStats {
		String resource;
		String configuration;
		Collection<StatisticsDTO> statistics = new LinkedList<StatisticsDTO>();

		public ExperimentStats(String resource, String configuration) {
			this.resource = resource;
		}

		public void addStats(StatisticsDTO stats) {
			statistics.add(stats);
		}

		public double getTotalDistance() {
			double totalDistance = 0;
			for (StatisticsDTO stat : statistics) {
				totalDistance += stat.totalDistance;
			}
			return totalDistance / statistics.size();
		}

		public long getPickupTardiness() {
			long totalPickupTardiness = 0;
			for (StatisticsDTO stat : statistics) {
				totalPickupTardiness += stat.pickupTardiness;
			}
			return totalPickupTardiness / statistics.size();
		}

		public long getDeliveryTardiness() {
			long totalDeliveryTardiness = 0;
			for (StatisticsDTO stat : statistics) {
				totalDeliveryTardiness += stat.deliveryTardiness;
			}
			return totalDeliveryTardiness / statistics.size();
		}

		public long getSimulationTime() {
			long totalSimulationTime = 0;
			for (StatisticsDTO stat : statistics) {
				totalSimulationTime += stat.simulationTime;
			}
			return totalSimulationTime / statistics.size();
		}

		public List<Boolean> getSimFinish() {
			List<Boolean> simFinish = new LinkedList<Boolean>();
			for (StatisticsDTO stat : statistics) {
				simFinish.add(stat.simFinish);
			}
			return simFinish;
		}

		public long getOverTime() {
			long totalOverTime = 0;
			for (StatisticsDTO stat : statistics) {
				totalOverTime += stat.overTime;
			}
			return totalOverTime / statistics.size();
		}

		public String toString() {
			String json = "{ \"totalDistance\" : " + getTotalDistance()
					+ ", \"pickupTardiness\" : " + getPickupTardiness()
					+ ", \"deliveryTardiness\" : " + getDeliveryTardiness()
					+ ", \"simulationTime\" : " + getSimulationTime()
					+ ", \"simFinish\" : "
					+ Arrays.toString(getSimFinish().toArray())
					+ ", \"overTime\" : " + getOverTime() + "}";
			return json;
		}
	}
}
