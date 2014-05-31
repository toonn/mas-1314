package mas;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.common.base.Optional;

import mas.SmartVehicle.BidMessage;
import mas.SmartVehicle.BidStore;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;

public class BestFutureSelection implements SelectStrategy {
	private int nrConsideredFutures;
	private int nrFutureBackers;
	/**
	 * Ratio needed between parcels, to change your intention.
	 */
	private double inertialThreshold;
	/**
	 * Agents will tolerate waiting for the start of a pickup/delivery window,
	 * for "punctuality"*100 % of the time it takes them to get to the
	 * location/destination of a parcel.
	 */
	private double punctuality;

	private double currentFutureValue = Double.MIN_VALUE;

	public BestFutureSelection() {
		this.nrConsideredFutures = 10;
		this.nrFutureBackers = 42;
		this.inertialThreshold = 1 + 0.1;
		this.punctuality = 0.1;
	}

	public BestFutureSelection(int nrFuturesToConsider,
			int nrFutureBackersToConsider, double inertia, double punctuality) {
		this.nrConsideredFutures = nrFuturesToConsider;
		this.nrFutureBackers = nrFutureBackersToConsider;
		this.inertialThreshold = inertia;
		this.punctuality = punctuality;
	}

	@Override
	public Parcel parcel(SmartVehicle thisVehicle, PDPModel pm, RoadModel rm,
			TimeLapse time, Optional<Parcel> curr, BidStore commBids, long seed) {
		BidMessage future = null;
		double cumulativeFutureCost = Double.NEGATIVE_INFINITY;

		List<BidMessage> backers = new LinkedList<BidMessage>();
		Collections
				.shuffle(
						new LinkedList<BidMessage>(commBids
								.senderMessages(thisVehicle)), new Random(seed));
		backers = backers.subList(0, Math.min(nrFutureBackers, backers.size()));

		LinkedList<BidMessage> futureCandidates = commBids.futures(thisVehicle,
				nrConsideredFutures);
		// commBids.futures returns a list sorted from highest to lowest bid
		BidMessage smallest = futureCandidates.peekLast();
		for (Parcel p : pm.getContents(thisVehicle)) {
			double pCost = thisVehicle.cost(pm, rm, thisVehicle.getPosition(),
					pm.getContents(thisVehicle), p);
			if (smallest == null || pCost > smallest.getBid()) {
				futureCandidates.offer(thisVehicle.new BidMessage(thisVehicle,
						p, pCost, thisVehicle.TTL, thisVehicle.getPosition()));
			}
		}
		for (BidMessage futureCandidate : futureCandidates) {
			Point futurePosition;
			List<Parcel> cargo = new LinkedList<Parcel>(
					pm.getContents(thisVehicle));
			Parcel candidateParcel = futureCandidate.getParcel();
			if (pm.containerContains(thisVehicle, candidateParcel)) {
				futurePosition = candidateParcel.getDestination();
			} else {
				futurePosition = futureCandidate.getPosition();
				cargo.add(candidateParcel);
			}

			double cumSum = 0;
			for (BidMessage backer : backers) {
				cumSum += thisVehicle.cost(pm, rm, futurePosition, cargo,
						backer.getParcel());
			}
			if (cumSum > cumulativeFutureCost) {
				cumulativeFutureCost = cumSum;
				future = futureCandidate;
			}

		}
		Parcel parcel = null;
		if (curr.isPresent()) {
			for (BidMessage bid : commBids.senderMessages(thisVehicle)) {
				if (bid.getParcel().equals(curr.get())) {
					currentFutureValue = 0;
					for (BidMessage backer : backers) {
						currentFutureValue += thisVehicle.cost(pm, rm,
								bid.getPosition(), pm.getContents(thisVehicle),
								backer.getParcel());
					}
					parcel = curr.get();
					break;
				}
			}

		}
		if (future != null
				&& (!curr.isPresent() || cumulativeFutureCost
						/ currentFutureValue > inertialThreshold)) {
			currentFutureValue = cumulativeFutureCost;
			parcel = future.getParcel();
		}

		if (parcel == null) {
			if (pm.getContents(thisVehicle).isEmpty()
					&& !futureCandidates.isEmpty()) {
				parcel = futureCandidates.poll().getParcel();
			} else {
				double parcelEndTime = Double.POSITIVE_INFINITY;
				Set<Parcel> cargo = pm.getContents(thisVehicle);
				for (Parcel p : cargo) {
					if (p.getDeliveryTimeWindow().begin < parcelEndTime) {
						parcel = p;
						parcelEndTime = p.getDeliveryTimeWindow().begin;
					}
				}
			}
		}

		// If we could still travel a considerable distance before being able to
		// handle the selected parcel, then abandon choice.
		// 3600000 convert from hours to ms
		// If the distance to the parcel is less
		if (parcel != null) {
			if (pm.containerContains(thisVehicle, parcel)
					&& parcel
							.getDeliveryTimeWindow()
							.isBeforeStart(
									time.getTime()
											+ Math.round((punctuality + 1)
													* (Point.distance(
															thisVehicle
																	.getPosition(),
															parcel.getDestination())
															/ thisVehicle
																	.getSpeed() * 3600000)))) {
				parcel = null;
			} else if (!pm.containerContains(thisVehicle, parcel)
					&& parcel
							.getPickupTimeWindow()
							.isBeforeStart(
									time.getTime()
											+ Math.round((punctuality + 1)
													* (Point.distance(
															thisVehicle
																	.getPosition(),
															parcel.getDestination())
															/ thisVehicle
																	.getSpeed() * 3600000)))) {
				parcel = null;
			}
		}
		return parcel;
	}

}
