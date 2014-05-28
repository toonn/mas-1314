package mas;

import org.apache.commons.lang3.builder.ToStringBuilder;
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
	final boolean smart;

	public Configuration(final boolean smart) {
		this.smart = smart;
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
		if (smart)
			creator = new Creator<AddVehicleEvent>() {
				@Override
				public boolean create(Simulator sim, AddVehicleEvent event) {
					return sim.register(new SmartVehicle(event.vehicleDTO));
				}
			};
		else
			creator = new Creator<AddVehicleEvent>() {
				@Override
				public boolean create(Simulator sim, AddVehicleEvent event) {
					return sim.register(new GreedyVehicle(event.vehicleDTO));
				}
			};
		return creator;
	}

	@Override
	public String toString() {
		if (smart) {
			return SmartVehicle.class.getSimpleName();
		} else {
			return GreedyVehicle.class.getSimpleName();
		}
	}

}
