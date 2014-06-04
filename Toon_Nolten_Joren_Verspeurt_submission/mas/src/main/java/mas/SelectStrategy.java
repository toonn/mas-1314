package mas;

import com.google.common.base.Optional;

import mas.SmartVehicle.BidStore;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;

public interface SelectStrategy {

	Parcel parcel(SmartVehicle thisVehicle, PDPModel pm, RoadModel rm,
			TimeLapse time, Optional<Parcel> curr, BidStore commBids, long seed);

}
