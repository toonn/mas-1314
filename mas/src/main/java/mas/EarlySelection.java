package mas;

import java.util.Set;

import mas.SmartVehicle.BidMessage;
import mas.SmartVehicle.BidStore;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;

import com.google.common.base.Optional;

public class EarlySelection implements SelectStrategy {

	@Override
	public Parcel parcel(SmartVehicle thisVehicle, PDPModel pm, RoadModel rm,
			TimeLapse time, Optional<Parcel> curr, BidStore commBids, long seed) {
		Parcel parcel = null;
		long parcelEndTime = Long.MAX_VALUE;
		for (BidMessage bid : commBids.senderMessages(thisVehicle)) {
			boolean inCargo = pm
					.containerContains(thisVehicle, bid.getParcel());
			if (!inCargo && !rm.containsObject(bid.getParcel())) {
				commBids.purge(bid);
			} else if (inCargo
					&& bid.getParcel().getDeliveryTimeWindow().end < parcelEndTime) {
				// Bug introduced when adding the 'vanishing' to the smart agent:
				// This will never occur, parcels that are picked up are vanished,
				// they will therefore not occur in commBids.
				parcel = bid.getParcel();
				parcelEndTime = parcel.getDeliveryTimeWindow().end;
			} else if (!inCargo
					&& bid.getParcel().getPickupTimeWindow().end < parcelEndTime) {
				parcel = bid.getParcel();
				parcelEndTime = parcel.getPickupTimeWindow().end;
			}
		}
		if (parcel == null) {
			Set<Parcel> cargo = pm.getContents(thisVehicle);
			for (Parcel p : cargo) {
				if (p.getDeliveryTimeWindow().begin < parcelEndTime) {
					parcel = p;
					parcelEndTime = p.getDeliveryTimeWindow().begin;
				}
			}
		}
		return parcel;
	}

}
