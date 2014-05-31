package mas;

import java.util.Collection;

import mas.SmartVehicle.BidStore;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;

public interface ValueStrategy {

	double assign(SmartVehicle thisVehicle, PDPModel pm, RoadModel rm,
			TimeLapse time, BidStore commBids, Point position,
			Collection<Parcel> cargo, Parcel parcel);

}
