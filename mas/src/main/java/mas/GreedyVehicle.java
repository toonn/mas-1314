package mas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.core.model.road.RoadModels;
import rinde.sim.core.model.road.RoadUser;
import rinde.sim.pdptw.common.DefaultVehicle;
import rinde.sim.pdptw.common.VehicleDTO;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

/**
 * Implementation of a very simple taxi agent. It moves to the closest customer,
 * picks it up, then delivers it, repeat.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
class GreedyVehicle extends DefaultVehicle {
	Optional<Parcel> curr = Optional.absent();

	public GreedyVehicle(VehicleDTO pDto) {
		super(pDto);
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = roadModel.get();
		final PDPModel pm = pdpModel.get();

		if (!time.hasTimeLeft()) {
			return;
		}
		if (!curr.isPresent() && !pm.getParcels(ParcelState.values()).isEmpty()) {
			List<Parcel> parcels = RoadModels.findClosestObjects(
					rm.getPosition(this), rm, Parcel.class, 10);
			List<Parcel> availableParcels = new ArrayList<Parcel>(
					Collections2.filter(parcels, new Predicate<Parcel>() {
						@Override
						public boolean apply(Parcel parcel) {
							return ParcelState.AVAILABLE == pm.getParcelState(parcel);
						}
					}));
			Parcel parcel = availableParcels.isEmpty() ? parcels.get(0)
					: availableParcels.get(0);
			curr = Optional.fromNullable(parcel);
		}

		if (curr.isPresent() && ParcelState.AVAILABLE == pm.getParcelState(curr.get())) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			if (!inCargo && !rm.containsObject(curr.get())) {
				curr = Optional.absent();
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, curr.get().getDestination(), time);
				if (rm.getPosition(this).equals(curr.get().getDestination())
						&& curr.get().getDeliveryTimeWindow()
								.isIn(time.getTime())) {
					// deliver when we arrive
					pm.deliver(this, curr.get(), time);
				}
			} else {
				// it is still available, go there as fast as possible
				rm.moveTo(this, curr.get(), time);
				if (rm.equalPosition(this, curr.get())
						&& ParcelState.AVAILABLE == pm.getParcelState(curr
								.get())) {
					// pickup customer
					pm.pickup(this, curr.get(), time);
				}
			}
		} else if (curr.isPresent()){
			curr = Optional.absent();
		}
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
		roadModel = Optional.of(pRoadModel);
		pdpModel = Optional.of(pPdpModel);
	}
}
