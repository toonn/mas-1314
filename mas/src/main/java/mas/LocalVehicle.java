package mas;

import static rinde.sim.core.model.pdp.PDPModel.ParcelState.ANNOUNCED;
import static rinde.sim.core.model.pdp.PDPModel.ParcelState.AVAILABLE;

import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.core.model.road.RoadModels;
import rinde.sim.pdptw.common.DefaultVehicle;
import rinde.sim.pdptw.common.VehicleDTO;

import com.google.common.base.Optional;

public abstract class LocalVehicle extends DefaultVehicle {

	protected static final RandomGenerator rng = new MersenneTwister(123);
	protected Point destination;
	protected long direction = 0;
	protected Optional<Parcel> curr = Optional.absent();
	protected double commRadius = 0.5;
	protected double randomMovementScalingFactor = 0.5 * commRadius;

	public LocalVehicle(VehicleDTO pDto) {
		super(pDto);
	}

	protected Collection<Parcel> getVisibleParcels(PDPModel pm, RoadModel rm) {
		Collection<Parcel> visibleParcels = RoadModels.findObjectsWithinRadius(
				getPosition(), rm, commRadius, Parcel.class);
		Iterator<Parcel> it = visibleParcels.iterator();
		while (it.hasNext()) {
			Parcel p = it.next();
			if (pm.getParcelState(p) != ANNOUNCED
					&& pm.getParcelState(p) != AVAILABLE)
				it.remove();
		}

		return visibleParcels;
	}

	protected Point pointAdd(Point p1, Point p2) {
		return Point.diff(p1, Point.divide(p2, -1));
	}

	protected Point movementVector() {
		Point destination = new Point(getPosition().x + Math.cos(direction)
				* randomMovementScalingFactor, getPosition().y
				+ Math.sin(direction) * randomMovementScalingFactor);
		return destination;
	}

	/**
	 * Assume the roadmodel is square.
	 */
	protected boolean inBounds(RoadModel rm, Point point) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Point boundPoint : rm.getBounds()) {
			if (boundPoint.x < min)
				min = boundPoint.x;
			if (boundPoint.x > max)
				max = boundPoint.x;
			if (boundPoint.y < min)
				min = boundPoint.y;
			if (boundPoint.y > max)
				max = boundPoint.y;
		}

		if (point.x >= min && point.x <= max && point.y >= min
				&& point.y <= max)
			return true;
		else
			return false;
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
		roadModel = Optional.of(pRoadModel);
		pdpModel = Optional.of(pPdpModel);
	}

	public Point getPosition() {
		return roadModel.get().getPosition(this);
	}

	public double getRadius() {
		return commRadius;
	}

}