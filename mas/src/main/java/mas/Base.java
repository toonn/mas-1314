package mas;

import static com.google.common.collect.Maps.newHashMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import javax.annotation.Nullable;
import javax.measure.Measure;
import javax.measure.unit.SI;

import mas.Renderer.Language;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Graph;
import rinde.sim.core.graph.MultiAttributeData;
import rinde.sim.core.model.pdp.DefaultPDPModel;
import rinde.sim.core.model.pdp.twpolicy.TardyAllowedPolicy;
import rinde.sim.core.model.pdp.twpolicy.TimeWindowPolicy;
import rinde.sim.core.model.road.GraphRoadModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.scenario.ScenarioController.UICreator;
import rinde.sim.serializers.DotGraphSerializer;
import rinde.sim.serializers.SelfCycleFilter;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.GraphRoadModelRenderer;
import rinde.sim.ui.renderers.PlaneRoadModelRenderer;
import rinde.sim.ui.renderers.RoadUserRenderer;
import rinde.sim.ui.renderers.UiSchema;

/**
 * Example showing a fleet of taxis that have to pickup and transport customers
 * around the city of Leuven.
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class Base {

    // time in ms
    // private static final long SERVICE_DURATION = 60000;
    private static final String MAP_FILE = "/data/maps/leuven-simple.dot";
    private static final Map<String, Graph<MultiAttributeData>> GRAPH_CACHE = newHashMap();

    private Base() {
    }

    /**
     * Starts the {@link Base}.
     * 
     * @param args
     */
    public static void main(@Nullable String[] args) {
        final long endTime = args != null && args.length >= 1 ? Long
                .parseLong(args[0]) : Long.MAX_VALUE;

        final String graphFile = args != null && args.length >= 2 ? args[1]
                : MAP_FILE;
        run(false, endTime, graphFile, new TardyAllowedPolicy(), 20, 10, "req_rapide_1_240_24");
    }

    /**
     * Starts the example.
     */
    public static Simulator run(final boolean testing, final long endTime,
            String graphFile, TimeWindowPolicy twPol, int num_vehicles, int capacity,
            String gendreauString) {

        // create a new simulator
        final RandomGenerator rng = new MersenneTwister(123);
        final Simulator simulator = new Simulator(rng, Measure.valueOf(1000L,
                SI.MILLI(SI.SECOND)));

        // use map of leuven
        final RoadModel roadModel = new GraphRoadModel(loadGraph(graphFile));
        final DefaultPDPModel pdpModel = new DefaultPDPModel(twPol);

        // configure simulator with models
//        simulator.register(roadModel);
//        simulator.register(pdpModel);
//        simulator.configure();

        // add taxis and parcels to simulator
        // for (int i = 0; i < num_vehicles; i++) {
        // simulator.register(new GreedyVehicle(roadModel
        // .getRandomPosition(rng), capacity));
        // }

        // simulator.addTickListener(new TickListener() {
        // @Override
        // public void tick(TimeLapse time) {
        // if (time.getStartTime() > endTime) {
        // simulator.stop();
        // } else if (rng.nextDouble() < .007) {
        // simulator.register(new Packet(roadModel
        // .getRandomPosition(rng), roadModel
        // .getRandomPosition(rng), SERVICE_DURATION,
        // SERVICE_DURATION, 1 + rng.nextInt(3)));
        // }
        // }
        //
        // @Override
        // public void afterTick(TimeLapse timeLapse) {
        // }

        final UICreator uic = new UICreator() {
            @Override
            public void createUI(Simulator sim) {
                final UiSchema uis = new UiSchema();
                uis.add(GreedyVehicle.class,
                        "/graphics/perspective/semi-truck-32.png");
                uis.add(DefaultParcel.class,
                        "/graphics/perspective/deliverypackage.png");
                final View.Builder view = View.create(simulator)
                        .with(new PlaneRoadModelRenderer())
                        .with(new RoadUserRenderer(uis, false))
                        .with(new Renderer(Language.ENGLISH))
                        .setTitleAppendix("MAS Project");
                if (testing) {
                    view.enableAutoClose().enableAutoPlay().setSpeedUp(64)
                            .stopSimulatorAtTime(60 * 60 * 1000);
                }
                view.show();
            }
        };

        final Gendreau06Scenario scenario = Gendreau06Parser
                .parser()
                .addFile(Base.class.getResourceAsStream("/" + gendreauString),
                        gendreauString).allowDiversion().parse().get(0);

        final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();

        simulator.register(scenario.createRoadModel());
        simulator.register(scenario.createPDPModel());
        simulator.configure();

        Experiment.build(objFunc).withRandomSeed(123)
                .addConfiguration(new Configuration(false))
                .addScenario(scenario).showGui(uic).repeat(1).perform();

        return simulator;
    }

    // load the graph file
    static Graph<MultiAttributeData> loadGraph(String name) {
        try {
            if (GRAPH_CACHE.containsKey(name)) {
                return GRAPH_CACHE.get(name);
            }
            final Graph<MultiAttributeData> g = DotGraphSerializer
                    .getMultiAttributeGraphSerializer(new SelfCycleFilter())
                    .read(Base.class.getResourceAsStream(name));

            GRAPH_CACHE.put(name, g);
            return g;
        } catch (final FileNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
