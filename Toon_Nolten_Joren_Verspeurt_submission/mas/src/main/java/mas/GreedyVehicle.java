package mas;

import java.util.Collection;

import com.google.common.base.Optional;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.Depot;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.core.model.road.RoadModels;
import rinde.sim.pdptw.common.VehicleDTO;

public class GreedyVehicle extends LocalVehicle {

	public GreedyVehicle(VehicleDTO pDto) {
		super(pDto);
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = roadModel.get();
		final PDPModel pm = pdpModel.get();

		if (!time.hasTimeLeft()) {
			return;
		}

		if (!curr.isPresent()) {
			Parcel parcel = null;
			Collection<Parcel> visibleParcels = getVisibleParcels(pm, rm);
			double minDistance = Double.POSITIVE_INFINITY;
			for (Parcel p : visibleParcels) {
				if (Point.distance(getPosition(), rm.getPosition(p)) < minDistance) {
					parcel = p;
					minDistance = Point.distance(getPosition(),
							rm.getPosition(p));
					destination = rm.getPosition(p);
				}
			}
			double deliveryDistance = Double.POSITIVE_INFINITY;
			for (Parcel delivery : pm.getContents(this)) {
				double distance = Point.distance(getPosition(),
						delivery.getDestination());
				if (delivery.getDeliveryTimeWindow().isAfterStart(
						time.getTime())
						&& distance < deliveryDistance) {
					parcel = delivery;
					deliveryDistance = distance;
					destination = delivery.getDestination();
				}
			}
			if (parcel == null) {
				long timewindowStart = Long.MAX_VALUE;
				for (Parcel delivery : pm.getContents(this)) {
					if (delivery.getDeliveryTimeWindow().begin < timewindowStart) {
						parcel = delivery;
						destination = delivery.getDestination();
						timewindowStart = delivery.getDeliveryTimeWindow().begin;
					}
				}
			}

			curr = Optional.fromNullable(parcel);
		}

		if (curr.isPresent()) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, destination, time);
				if (rm.getPosition(this).equals(curr.get().getDestination())
						&& curr.get().getDeliveryTimeWindow()
								.isAfterStart(time.getTime())) {
					// deliver when we arrive
					pm.deliver(this, curr.get(), time);
				}
			} else if (ParcelState.AVAILABLE == pm.getParcelState(curr.get())) {
				// it is still available, go there as fast as possible
				rm.moveTo(this, destination, time);
				if (rm.equalPosition(this, curr.get())
						&& ParcelState.AVAILABLE == pm.getParcelState(curr
								.get())) {
					// pickup customer
					pm.pickup(this, curr.get(), time);
				}
			}

			destination = null;
			curr = Optional.absent();
		} else if (pm.getParcels(ParcelState.ANNOUNCED, ParcelState.AVAILABLE)
				.isEmpty()) {
			rm.moveTo(this, RoadModels.findClosestObject(rm.getPosition(this),
					rm, Depot.class), time);
		} else {
			// Wander around
			if (null == destination) {
				// if we don't have a destination randomly pick one
				direction = rng.nextLong();
			}

			destination = movementVector();

			while (!inBounds(rm, destination)) {
				direction = rng.nextLong();
				destination = movementVector();
			}

			rm.moveTo(this, destination, time);
		}
	}
}
