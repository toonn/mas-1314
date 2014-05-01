package mas;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.core.model.road.RoadModels;
import rinde.sim.pdptw.common.DefaultVehicle;
import rinde.sim.pdptw.common.VehicleDTO;

import com.google.common.base.Optional;

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
  public void afterTick(TimeLapse timeLapse) {}

  @Override
  protected void tickImpl(TimeLapse time) {
    final RoadModel rm = roadModel.get();
    final PDPModel pm = pdpModel.get();

    if (!time.hasTimeLeft()) {
      return;
    }
    if (!curr.isPresent()) {
      curr = Optional.fromNullable(RoadModels.findClosestObject(
          rm.getPosition(this), rm, Parcel.class));
    }

    if (curr.isPresent()) {
      final boolean inCargo = pm.containerContains(this, curr.get());
      // sanity check: if it is not in our cargo AND it is also not on the
      // RoadModel, we cannot go to curr anymore.
      if (!inCargo && !rm.containsObject(curr.get())) {
        curr = Optional.absent();
      } else if (inCargo) {
        // if it is in cargo, go to its destination
        rm.moveTo(this, curr.get().getDestination(), time);
        if (rm.getPosition(this).equals(curr.get().getDestination())) {
          // deliver when we arrive
          pm.deliver(this, curr.get(), time);
        }
      } else {
        // it is still available, go there as fast as possible
        rm.moveTo(this, curr.get(), time);
        if (rm.equalPosition(this, curr.get())) {
          // pickup customer
          pm.pickup(this, curr.get(), time);
        }
      }
    }
  }

  @Override
  public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
    roadModel = Optional.of(pRoadModel);
    pdpModel = Optional.of(pPdpModel);
  }
}
