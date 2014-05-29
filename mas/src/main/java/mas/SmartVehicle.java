package mas;

import static rinde.sim.core.model.pdp.PDPModel.ParcelState.ANNOUNCED;
import static rinde.sim.core.model.pdp.PDPModel.ParcelState.AVAILABLE;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.communication.CommunicationAPI;
import rinde.sim.core.model.communication.CommunicationUser;
import rinde.sim.core.model.communication.Message;
import rinde.sim.core.model.pdp.Depot;
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
 */
class SmartVehicle extends DefaultVehicle implements CommunicationUser {

	private CommunicationAPI cm;
	private double commRadius = 0.5;
	private double commReliability = 0.8;
	private final Map<Parcel, BidMessage> ownBids = new HashMap<Parcel, SmartVehicle.BidMessage>();

	private int commCounter = 0;
	private final Map<SmartVehicle, Integer> commWith = new HashMap<SmartVehicle, Integer>();
	public static final String C_MALACHITE = "color.Malachite";
	public static final String C_PERIWINKLE = "color.Periwinkle";
	public static final String C_VERMILLION = "color.Vermillion";

	private static final RandomGenerator rng = new MersenneTwister(123);
	private long direction = 0;

	private Optional<Parcel> curr = Optional.absent();
	private int TTL = 5;
	private final double scalingFactor = 0.5 * commRadius;
	private Point destination;
	private int nrConsideredFutures = 10;
	private int nrFutureBackers = 42;
	private double inertialThreshold = 1 + 0.1;

	private double currentFutureValue = Double.MIN_VALUE;

	private final BidStore commBids = new BidStore();

	SmartVehicle(VehicleDTO dto) {
		super(dto);
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
		incrementCommWith();
		refreshCommWith();

		// Forget parcels you haven't seen or heard about for long enough
		// from ownBids
		// The bids in commBids get their ttl decremented anyway, but
		// if they're in ownBids but not in commBids they need to have
		// their ttl decremented
		Set<Parcel> knownParcels = commBids.senderParcels(this);
		Iterator<Parcel> it = ownBids.keySet().iterator();
		while (it.hasNext()) {
			Parcel parcel = it.next();
			if (ownBids.get(parcel).getTtl() <= 0) {
				it.remove();
			} else if (!knownParcels.contains(parcel)) {
				ownBids.get(parcel).decrementTtl();
			}
		}
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = roadModel.get();
		final PDPModel pm = pdpModel.get();

		if (!time.hasTimeLeft()) {
			return;
		}

		// Look at all the parcels you can see (new or not)
		Set<Parcel> parcelSet = new HashSet<Parcel>(getVisibleParcels(pm, rm));
		// add the ones you've heard about or are bidding on yourself
		parcelSet.removeAll(commBids.getParcels());
		// Bid on all of them if appropriate
		for (Parcel parcel : parcelSet) {
			storeOwnBid(new BidMessage(this, parcel, cost(pm, rm,
					getPosition(), pm.getContents(this), parcel), TTL));
		}
		for (BidMessage bid : commBids.getBids()) {
			storeOwnBid(new BidMessage(this, bid.getParcel(), cost(pm, rm,
					getPosition(), pm.getContents(this), bid.getParcel()), TTL,
					bid.getPosition()));
		}

		sendBid();

		// Select current obsession
		// if (!curr.isPresent()) {
		curr = Optional.fromNullable(selectParcel(pm, rm));
		// }

		// Deal with it
		if (curr.isPresent()) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			if (!inCargo && !rm.containsObject(curr.get())) {
				curr = Optional.absent();
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, curr.get().getDestination(), time);
				if (getPosition().equals(curr.get().getDestination())
						&& curr.get().getDeliveryTimeWindow()
								.isAfterStart(time.getTime())) {
					// deliver when we arrive
					pm.deliver(this, curr.get(), time);
				}
			} else {
				// it is still available, go there as fast as possible
				rm.moveTo(this, curr.get(), time);
				if (rm.equalPosition(this, curr.get())
						&& AVAILABLE == pm.getParcelState(curr.get())) {
					// pickup parcel
					pm.pickup(this, curr.get(), time);
					commBids.purge(ownBids.get(curr.get()));
					ownBids.remove(curr.get());
				}
			}
		} else if (pm.getParcels(ANNOUNCED, AVAILABLE).isEmpty()) {
			// If there are no more parcels to be delivered go back to the depot
			rm.moveTo(this, RoadModels.findClosestObject(getPosition(), rm,
					Depot.class), time);
		} else {
			// If there are still parcels to be delivered but we can't go to
			// pick up
			// any of them wander around toward parcels but away from agents
			Collection<Parcel> parcels = RoadModels.findObjectsWithinRadius(
					getPosition(), rm, commRadius, Parcel.class);
			Collection<SmartVehicle> agents = RoadModels
					.findObjectsWithinRadius(getPosition(), rm, commRadius,
							SmartVehicle.class);
			Point newDestination = new Point(0, 0);
			for (Parcel parcel : parcels) {
				newDestination = pointAdd(newDestination,
						Point.diff(rm.getPosition(parcel), getPosition()));
			}
			for (SmartVehicle agent : agents) {
				newDestination = Point.diff(newDestination, Point.divide(
						Point.diff(rm.getPosition(agent), getPosition()), 0.1));
			}
			if (!new Point(0, 0).equals(newDestination)) {
				destination = pointAdd(
						getPosition(),
						Point.divide(newDestination,
								Point.distance(new Point(0, 0), newDestination)
										/ scalingFactor));
			} else if (null != destination) {
				//
				destination = pointAdd(Point.divide(
						Point.diff(destination, getPosition()),
						Point.distance(destination, getPosition())
								/ scalingFactor), getPosition());
			} else {
				// only happens in the beginning, if we don't have a destination
				// randomly pick one
				destination = rm.getRandomPosition(rng);
			}

			while (!inBounds(rm, destination)) {
				direction = rng.nextLong();
				destination = movementVector(time);
			}
			rm.moveTo(this, destination, time);
		}
	}

	private void storeOwnBid(BidMessage bid) {
		// If it's not in ownBids it's new so bid on it anyway
		// If you're bidding the same amount as the last time don't
		// update the bid so the ttl and tiebreaker stay the same
		if (!ownBids.containsKey(bid.getParcel())
				|| bid.getBid() != ownBids.get(bid.getParcel()).getBid()) {
			ownBids.put(bid.getParcel(), bid);
			commBids.ensconce(bid);
		}
	}

	private Point pointAdd(Point p1, Point p2) {
		return Point.diff(p1, Point.divide(p2, -1));
	}

	private Point movementVector(TimeLapse time) {
		Point destination = new Point(getPosition().x + Math.cos(direction)
				* scalingFactor, getPosition().y + Math.sin(direction)
				* scalingFactor);
		return destination;
	}

	/**
	 * Assume the roadmodel is square.
	 */
	private boolean inBounds(RoadModel rm, Point point) {
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

	private double cost(PDPModel pm, RoadModel rm, Point position,
			Collection<Parcel> cargo, Parcel parcel) {
		return 10;
	}

	private Collection<Parcel> getVisibleParcels(PDPModel pm, RoadModel rm) {
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

	private Parcel selectParcel(PDPModel pm, RoadModel rm) {
		// voor tien hoogste bids, cumulatieve gewogen som van bids, pas met een
		// 'serieuze' verbetering wisselen.
		BidMessage future = null;
		double cumulativeFutureCost = Double.NEGATIVE_INFINITY;

		List<BidMessage> backers = new LinkedList<BidMessage>();
		Collections.shuffle(
				new LinkedList<BidMessage>(commBids.senderMessages(this)),
				new Random(rng.nextLong()));
		backers = backers.subList(0, Math.min(nrFutureBackers, backers.size()));

		LinkedList<BidMessage> futureCandidates = commBids.futures(this,
				nrConsideredFutures);
		// commBids.futures returns a list sorted from highest to lowest bid
		BidMessage smallest = futureCandidates.peekLast();
		for (Parcel p : pm.getContents(this)) {
			double pCost = cost(pm, rm, getPosition(), pm.getContents(this), p);
			if (smallest == null || pCost > smallest.getBid()) {
				futureCandidates.offer(new BidMessage(this, p, pCost, TTL,
						getPosition()));
			}
		}
		for (BidMessage futureCandidate : futureCandidates) {
			Point futurePosition;
			Collection<Parcel> cargo = new LinkedList<Parcel>(
					pm.getContents(this));
			Parcel candidateParcel = futureCandidate.getParcel();
			if (pm.containerContains(this, candidateParcel)) {
				futurePosition = candidateParcel.getDestination();
			} else {
				if (!getVisibleParcels(pm, rm).contains(candidateParcel)
						&& Point.distance(getPosition(),
								futureCandidate.getPosition()) < commRadius) {
					commBids.purge(futureCandidate);
					ownBids.remove(futureCandidate);
					continue;
				}
				futurePosition = futureCandidate.getPosition();
				cargo.add(candidateParcel);
			}

			double cumSum = 0;
			for (BidMessage backer : backers) {
				cumSum += cost(pm, rm, futurePosition, cargo,
						backer.getParcel());
			}
			if (cumSum > cumulativeFutureCost) {
				cumulativeFutureCost = cumSum;
				future = futureCandidate;
			}

		}
		Parcel parcel = null;
		if (curr.isPresent()) {
			for (BidMessage bid : commBids.senderMessages(this)) {
				if (bid.getParcel().equals(curr.get())) {
					parcel = curr.get();
					break;
				}
			}
		}
		if (future != null
				&& (!curr.isPresent() || cumulativeFutureCost
						/ currentFutureValue > inertialThreshold))
			parcel = future.getParcel();

		if (parcel == null) {
			if (pm.getContents(this).isEmpty() && !futureCandidates.isEmpty()) {
				parcel = futureCandidates.poll().getParcel();
			} else {
				double parcelEndTime = Double.POSITIVE_INFINITY;
				Set<Parcel> cargo = pm.getContents(this);
				for (Parcel p : cargo) {
					if (p.getDeliveryTimeWindow().begin < parcelEndTime) {
						parcel = p;
						parcelEndTime = p.getDeliveryTimeWindow().begin;
					}
				}
			}
		}
		return parcel;
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
		roadModel = Optional.of(pRoadModel);
		pdpModel = Optional.of(pPdpModel);
	}

	@Override
	public void setCommunicationAPI(CommunicationAPI api) {
		cm = api;
	}

	@Override
	public Point getPosition() {
		return roadModel.get().getPosition(this);
	}

	@Override
	public double getRadius() {
		return commRadius;
	}

	@Override
	public double getReliability() {
		return commReliability;
	}

	@Override
	public void receive(Message message) {
		commCounter++;
		try {
			BidMessage bidMessage = (BidMessage) message;
			commBids.ensconce(bidMessage);
		} catch (ClassCastException cce) {
			cce.printStackTrace();
		}
	}

	private void sendBid() {
		Collection<SmartVehicle> receivers = RoadModels
				.findObjectsWithinRadius(getPosition(), getRoadModel(),
						commRadius, SmartVehicle.class);
		receivers.remove(this);
		if (receivers.isEmpty())
			return;
		BidMessage bid = commBids.yoink();
		if (bid != null && cm != null) {
			cm.broadcast(bid);
			for (SmartVehicle receiver : receivers) {
				commWith.put(receiver, 0);
			}
		}
	}

	private void incrementCommWith() {
		Iterator<SmartVehicle> it = commWith.keySet().iterator();
		while (it.hasNext()) {
			SmartVehicle key = it.next();
			commWith.put(key, commWith.get(key) + 1);

		}
	}

	private void refreshCommWith() {
		Iterator<SmartVehicle> it = commWith.keySet().iterator();
		while (it.hasNext()) {
			SmartVehicle key = it.next();
			if (commWith.get(key) > 100) {
				it.remove();
			}
		}
	}

	public String getNoReceived() {
		return "" + commCounter;
	}

	public Set<SmartVehicle> getCommunicatedWith() {
		return commWith.keySet();
	}

	private class BidMessage extends Message {
		private Parcel parcel;
		private double bid;
		private int ttl;
		private final long tiebreaker;
		private final Point position;
		private final CommunicationUser originalSender;

		public BidMessage(CommunicationUser sender, Parcel parcel, double bid,
				int timeToLive) {
			super(sender);
			this.originalSender = sender;
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
			this.tiebreaker = rng.nextLong();
			this.position = getRoadModel().getPosition(parcel);
		}

		public BidMessage(CommunicationUser sender, Parcel parcel, double bid,
				int timeToLive, Point position) {
			super(sender);
			this.originalSender = sender;
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
			this.tiebreaker = rng.nextLong();
			this.position = position;
		}

		private BidMessage(CommunicationUser sender,
				CommunicationUser originalSender, Parcel parcel, double bid,
				int timeToLive, long tiebreaker, Point position) {
			super(sender);
			this.originalSender = originalSender;
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
			this.tiebreaker = tiebreaker;
			this.position = position;
		}

		@Override
		public BidMessage clone() throws CloneNotSupportedException {
			return (BidMessage) super.clone();

		}

		public CommunicationUser getOriginalSender() {
			return originalSender;
		}

		public Parcel getParcel() {
			return parcel;
		}

		public double getBid() {
			return bid;
		}

		public int getTtl() {
			return ttl;
		}

		public void decrementTtl() {
			ttl--;
		}

		public long getTiebreaker() {
			return tiebreaker;
		}

		public Point getPosition() {
			return position;
		}

		public BidMessage copy(CommunicationUser newSender) {
			return new BidMessage(newSender, originalSender, parcel, bid, ttl,
					tiebreaker, position);
		}

	}

	private class BidStore {
		private final Map<Parcel, BidMessage> bids = new HashMap<Parcel, BidMessage>();
		private final LinkedList<BidMessage> queue = new LinkedList<BidMessage>();
		private final PriorityQueue<BidMessage> pqueue =

		new PriorityQueue<BidMessage>(nrConsideredFutures,
				new Comparator<BidMessage>() {

					@Override
					public int compare(BidMessage o1, BidMessage o2) {
						// Reverse ordering
						if (o1.getBid() > o2.getBid())
							return -1;
						else if (o1.getBid() < o2.getBid())
							return 1;
						else
							return 0;
					}
				});

		public void ensconce(BidMessage bidMessage) {
			Parcel parcel = bidMessage.getParcel();
			if (bids.containsKey(parcel)) {
				final BidMessage oldBid = bids.get(parcel);
				if (bidMessage.getBid() > oldBid.getBid()) {
					purge(oldBid);
					queue.offer(bidMessage);
					pqueue.offer(bidMessage);
					bids.put(parcel, bidMessage);
				} else if (bidMessage.getBid() == oldBid.getBid()
						&& bidMessage.getTiebreaker() > oldBid.getTiebreaker()) {
					queue.set(queue.indexOf(oldBid), bidMessage);
					pqueue.remove(oldBid);
					pqueue.offer(bidMessage);
					bids.put(parcel, bidMessage);
				} else if (bidMessage.getOriginalSender() == oldBid
						.getOriginalSender()) {
					queue.set(queue.indexOf(oldBid), bidMessage);
					pqueue.remove(oldBid);
					pqueue.offer(bidMessage);
					bids.put(parcel, bidMessage);
				}
			} else {
				queue.offer(bidMessage);
				pqueue.offer(bidMessage);
				bids.put(parcel, bidMessage);
			}
		}

		public Collection<Parcel> getParcels() {
			return bids.keySet();
		}

		public Collection<BidMessage> getBids() {
			return bids.values();
		}

		public Set<BidMessage> senderMessages(CommunicationUser originalSender) {
			Set<BidMessage> sendersBids = new HashSet<SmartVehicle.BidMessage>();
			for (BidMessage bid : bids.values()) {
				if (bid.getOriginalSender() == originalSender) {
					sendersBids.add(bid);
				}
			}
			return sendersBids;
		}

		public Set<Parcel> senderParcels(CommunicationUser originalSender) {
			Set<Parcel> sendersBids = new HashSet<Parcel>();
			for (BidMessage bid : bids.values()) {
				if (bid.getOriginalSender() == originalSender) {
					sendersBids.add(bid.getParcel());
				}
			}
			return sendersBids;
		}

		public LinkedList<BidMessage> futures(CommunicationUser sender,
				int nrFutures) {
			LinkedList<BidMessage> futures = new LinkedList<BidMessage>();
			for (int i = 0; i < Math.min(nrFutures, pqueue.size()); i++) {
				futures.add(pqueue.poll());
			}
			pqueue.addAll(futures);

			return futures;
		}

		public BidMessage yoink() {
			BidMessage bid = queue.poll();
			if (bid == null)
				return null;
			bid.decrementTtl();
			if (bid.getTtl() >= 0)
				queue.offer(bid);
			else {
				purge(bid);
				return yoink();
			}
			return bid.copy(SmartVehicle.this);
		}

		public void purge(BidMessage target) {
			bids.remove(target.getParcel());
			queue.remove(target);
			pqueue.remove(target);
		}
	}
}
