package mas;

import java.util.Collection;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;

public interface ValueStrategy {

	double assign(PDPModel pm, RoadModel rm, Point position,
			Collection<Parcel> cargo, Parcel parcel);

}
