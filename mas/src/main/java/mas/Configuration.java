package mas;

import org.apache.commons.math3.random.MersenneTwister;

import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.communication.CommunicationModel2;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class Configuration extends DefaultMASConfiguration {

	private ExperimentParameters params;

	public Configuration(ExperimentParameters params) {
		this.params = params;
	}

	@Override
	public ImmutableList<? extends SupplierRng<? extends Model<?>>> getModels() {
		return ImmutableList.of(new DefaultSupplierRng<CommunicationModel2>() {
			@Override
			public CommunicationModel2 get(long seed) {
				return new CommunicationModel2(new MersenneTwister(seed));
			}
		});
	}

	@Override
	public Optional<? extends Creator<AddParcelEvent>> getParcelCreator() {
		return Optional.of(new Creator<AddParcelEvent>() {
			@Override
			public boolean create(Simulator sim, AddParcelEvent event) {
				// all parcels are accepted by default
				return sim.register(new DefaultParcel(event.parcelDTO));
			}
		});
	}

	@Override
	public Creator<AddVehicleEvent> getVehicleCreator() {
		Creator<AddVehicleEvent> creator;
		if (params.smart)
			creator = new Creator<AddVehicleEvent>() {
				@Override
				public boolean create(Simulator sim, AddVehicleEvent event) {
					return sim.register(new SmartVehicle(event.vehicleDTO,
							params.selectStrategy, params.valueStrategy,
							params.commRadius, params.commReliability,
							params.timeToLive,
							params.randomMovementScalingfactor,
							params.roadUserInfluenceOnRandomWalk));
				}
			};
		else if (ExperimentParameters.GREEDY.name().equals(params.name()))
			creator = new Creator<AddVehicleEvent>() {
				@Override
				public boolean create(Simulator sim, AddVehicleEvent event) {
					return sim.register(new GreedyVehicle(event.vehicleDTO));
				}
			};

		else
			creator = new Creator<AddVehicleEvent>() {
				@Override
				public boolean create(Simulator sim, AddVehicleEvent event) {
					return sim.register(new GreedyGlobalVehicle(
							event.vehicleDTO));
				}
			};
		return creator;
	}

	@Override
	public String toString() {
		return params.toString();
	}

	public enum ExperimentParameters {
		GREEDY_GLOBAL(false, null, null, 0, 0, 0, 0, 0),
		GREEDY(false, null, null, 0, 0, 0, 0, 0),
		EARLY_TRIVIAL(true, new EarlySelection(), new TrivialValueStrategy(),
				0.5, 0.8, 5, 0.5, 0.03),
		EARLY_SIMPLE(true, new EarlySelection(), new SimpleValueStrategy(),
				0.5, 0.8, 5, 0.5, 0.03),
		BESTFUTURE_TRIVIAL(true, new BestFutureSelection(),
				new TrivialValueStrategy(), 0.5, 0.8, 5, 0.5, 0.03),
		BESTFUTURE_SIMPLE_LCommR_LRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.2, 0.8, 5, 0.5, 0.02),
		BESTFUTURE_SIMPLE_MCommR_LRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.5, 0.8, 5, 0.5, 0.02),
		BESTFUTURE_SIMPLE_HCommR_LRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 1, 0.8, 5, 0.5, 0.02),
		BESTFUTURE_SIMPLE_LCommR_MRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.2, 0.8, 5, 0.5, 0.03),
		BESTFUTURE_SIMPLE_Defaults(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.5, 0.8, 5, 0.5, 0.03),
		BESTFUTURE_SIMPLE_HCommR_MRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 1, 0.8, 5, 0.5, 0.03),
		BESTFUTURE_SIMPLE_LCommR_HRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.2, 0.8, 5, 0.5, 0.05),
		BESTFUTURE_SIMPLE_MCommR_HRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.5, 0.8, 5, 0.5, 0.05),
		BESTFUTURE_SIMPLE_HCommR_HRUI(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 1, 0.8, 5, 0.5, 0.05),
		BESTFUTURE_SIMPLE_LCommRel(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.5, 0.4, 5, 0.5, 0.03),
		BESTFUTURE_SIMPLE_LTTL(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.5, 0.8, 2, 0.5, 0.03),
		BESTFUTURE_SIMPLE_HTTL(true, new BestFutureSelection(),
				new SimpleValueStrategy(), 0.5, 0.8, 10, 0.5, 0.03);

		public boolean smart;
		public SelectStrategy selectStrategy;
		public ValueStrategy valueStrategy;
		public double commRadius;
		public double commReliability;
		public int timeToLive;
		public double randomMovementScalingfactor;
		public double roadUserInfluenceOnRandomWalk;

		private ExperimentParameters(boolean smart,
				SelectStrategy selectStrategy, ValueStrategy valueStrategy,
				double commRadius, double commReliability, int timeToLive,
				double randomMovementScalingfactor,
				double roadUserInfluenceOnRandomWalk) {
			this.smart = smart;
			this.selectStrategy = selectStrategy;
			this.valueStrategy = valueStrategy;
			this.commRadius = commRadius;
			this.commReliability = commReliability;
			this.timeToLive = timeToLive;
			this.randomMovementScalingfactor = randomMovementScalingfactor;
			this.roadUserInfluenceOnRandomWalk = roadUserInfluenceOnRandomWalk;

		}

		@Override
		public String toString() {
			return name().toLowerCase().replace("_", " ");
		}
	}
}
