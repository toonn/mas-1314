package mas;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import mas.SmartVehicle.BidStore;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;

public class SimpleValueStrategy implements ValueStrategy {
	@Override
	public double assign(SmartVehicle thisVehicle, PDPModel pm, RoadModel rm,
			TimeLapse time, BidStore commBids, Point position,
			Collection<Parcel> cargoIn, Parcel parcel) {

		Set<Parcel> cargo = new HashSet<Parcel>(cargoIn);
		boolean inCargo = pm.containerContains(thisVehicle, parcel);
		if (inCargo)
			cargo.remove(parcel);

		double distance = Point.distance(position, parcel.getDestination());
		if (!inCargo) {
			distance = commBids.position(parcel) != null ? Point.distance(
					position, commBids.position(parcel)) : Point.distance(
					position, rm.getPosition(parcel));
		}

		double arrivalTime = time.getTime() + distance / thisVehicle.getSpeed()
				* 3600000;
		double parcelWindowStart = inCargo ? parcel.getDeliveryTimeWindow().begin
				: parcel.getPickupTimeWindow().begin;
		double timePenalty = Math.abs(parcelWindowStart - arrivalTime);

		// Between 2^52 and 2^53, doubles are integers.
		return (2 ^ 53) / (cargo.size() * timePenalty);
	}
}
