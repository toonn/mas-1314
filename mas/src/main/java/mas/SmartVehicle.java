package mas;

import static rinde.sim.core.model.pdp.PDPModel.ParcelState.ANNOUNCED;
import static rinde.sim.core.model.pdp.PDPModel.ParcelState.AVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.commons.math3.random.MersenneTwister;
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
import rinde.sim.pdptw.common.VehicleDTO;

import com.google.common.base.Optional;

/**
 * Implementation of a very simple taxi agent. It moves to the closest customer,
 * picks it up, then delivers it, repeat.
 */
class SmartVehicle extends LocalVehicle implements CommunicationUser {

	public static final String C_MALACHITE = "color.Malachite";
	public static final String C_PERIWINKLE = "color.Periwinkle";
	public static final String C_VERMILLION = "color.Vermillion";

	private CommunicationAPI cm;
	private int commCounter = 0;
	private final Map<SmartVehicle, Integer> commWith = new HashMap<SmartVehicle, Integer>();

	private final Set<Parcel> vanishedParcels = new HashSet<Parcel>();
	private final LinkedList<Parcel> vanishedDiscoveries = new LinkedList<Parcel>();

	private double commReliability = 0.8;
	public final int TTL;

	private double roadUserInfluenceOnRandomWalk = 0.03;
	private final SelectStrategy select;
	private final ValueStrategy value;
	// END: parameters

	private final BidStore commBids = new BidStore();

	SmartVehicle(VehicleDTO dto) {
		super(dto);
		this.select = new BestFutureSelection();
		this.value = new SimpleValueStrategy();
		this.commRadius = 0.5;
		this.commReliability = 0.8;
		this.TTL = 5;
		this.randomMovementScalingFactor = 0.5 * commRadius;
		this.roadUserInfluenceOnRandomWalk = 0.03;
	}

	SmartVehicle(VehicleDTO dto, SelectStrategy selectStrategy,
			ValueStrategy valueStrategy, double commRadius,
			double commReliability, int timeToLive,
			double randomMovementScalingfactor,
			double roadUserInfluenceOnRandomWalk) {
		super(dto);
		this.select = selectStrategy;
		this.value = valueStrategy;
		this.commRadius = commRadius;
		this.commReliability = commReliability;
		this.TTL = timeToLive;
		this.randomMovementScalingFactor = randomMovementScalingfactor
				* commRadius;
		this.roadUserInfluenceOnRandomWalk = roadUserInfluenceOnRandomWalk;
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
		incrementCommWith();
		refreshCommWith();
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = roadModel.get();
		final PDPModel pm = pdpModel.get();

		if (!time.hasTimeLeft()) {
			return;
		}

		// Record vanished parcels.
		Collection<BidMessage> allBids = new ArrayList<BidMessage>(
				commBids.getBids());
		for (BidMessage bid : allBids) {
			if (!pm.containerContains(this, bid.getParcel())
					&& !getVisibleParcels(pm, rm).contains(bid.getParcel())
					&& Point.distance(getPosition(), bid.getPosition()) < commRadius) {
				discoverVanished(bid.getParcel());
			}
		}

		// Look at all the parcels you can see (new or not)
		Set<Parcel> parcelSet = new HashSet<Parcel>(getVisibleParcels(pm, rm));
		// add the ones you've heard about or are bidding on yourself
		parcelSet.removeAll(commBids.getParcels());
		// Bid on all of them if appropriate
		for (Parcel parcel : parcelSet) {
			commBids.ensconce(new BidMessage(this, parcel, cost(pm, rm, time,
					getPosition(), pm.getContents(this), parcel), TTL));
		}
		for (BidMessage bid : new ArrayList<BidMessage>(commBids.getBids())) {
			commBids.ensconce(new BidMessage(this, bid.getParcel(), cost(pm,
					rm, time, getPosition(), pm.getContents(this),
					bid.getParcel()), TTL, bid.getPosition()));
		}

		sendBid();

		// Select current obsession
		curr = Optional.fromNullable(select.parcel(this, pm, rm, time, curr,
				commBids, rng.nextLong()));

		// Deal with it
		if (curr.isPresent()) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			if (inCargo) {
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
				Point pickupPosition = commBids.position(curr.get());
				if (pickupPosition != null) {
					rm.moveTo(this, pickupPosition, time);
				}
				if (getPosition().equals(pickupPosition)
						&& AVAILABLE == pm.getParcelState(curr.get())) {
					// pickup parcel
					pm.pickup(this, curr.get(), time);
					discoverVanished(curr.get());
				}
			}
		} else if (pm.getParcels(ANNOUNCED, AVAILABLE).isEmpty()) {
			// If there are no more parcels to be delivered go back to the depot
			rm.moveTo(this, RoadModels.findClosestObject(getPosition(), rm,
					Depot.class), time);
		} else {
			// If there are still parcels to be delivered but we can't go to
			// pick up any of them wander around toward parcels but away from
			// agents
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
				// 0.2: agents repel 5 times more than parcels attract
				newDestination = Point.diff(newDestination, Point.divide(
						Point.diff(rm.getPosition(agent), getPosition()), 0.2));
			}
			// if parcels or agents influence this agent, newDestination is a
			// 'unit vector' representing this influence.
			if (!new Point(0, 0).equals(newDestination)) {
				newDestination = Point.divide(newDestination,
						Point.distance(new Point(0, 0), newDestination)
								/ randomMovementScalingFactor);
			}
			// if the currently chosen (random) destination has not been
			// reached, rescale it to a 'unit vector'.
			if (null == destination || destination.equals(getPosition())) {
				// if we don't have a destination randomly pick one
				destination = rm.getRandomPosition(rng);
			}
			destination = Point.divide(Point.diff(destination, getPosition()),
					Point.distance(destination, getPosition())
							/ randomMovementScalingFactor);

			// 'unit vector'
			destination = pointAdd(Point.divide(destination,
					1 / (1 - roadUserInfluenceOnRandomWalk)), Point.divide(
					newDestination, 1 / roadUserInfluenceOnRandomWalk));
			// 'destination'
			destination = pointAdd(getPosition(), destination);

			while (!inBounds(rm, destination)) {
				direction = rng.nextLong();
				destination = movementVector();
			}

			rm.moveTo(this, destination, time);
		}
	}

	private void discoverVanished(Parcel vanished) {
		vanishedDiscoveries.offer(vanished);
		while (vanishedDiscoveries.size() > 10) {
			vanishedDiscoveries.poll();
		}
		vanishedParcels.add(vanished);
		commBids.purge(vanished);
	}

	public double cost(PDPModel pm, RoadModel rm, TimeLapse time,
			Point position, Collection<Parcel> cargo, Parcel parcel) {
		return value.assign(this, pm, rm, time, commBids, position, cargo,
				parcel);
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

	@Override
	public void receive(Message message) {
		commCounter++;
		try {
			BidMessage bidMessage = (BidMessage) message;
			if (!vanishedParcels.contains(bidMessage.getParcel()))
				commBids.ensconce(bidMessage);
			vanishedParcels.addAll(bidMessage.getVanished());
		} catch (ClassCastException cce) {
			cce.printStackTrace();
		}
	}

	@Override
	public void setCommunicationAPI(CommunicationAPI api) {
		cm = api;
	}

	@Override
	public double getReliability() {
		return commReliability;
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
		return new HashSet<SmartVehicle>(commWith.keySet());
	}

	public class BidMessage extends Message {
		private Parcel parcel;
		private double bid;
		private int ttl;
		private final long tiebreaker;
		private final Point position;
		private final CommunicationUser originalSender;
		private final List<Parcel> vanished;

		public BidMessage(CommunicationUser sender, Parcel parcel, double bid,
				int timeToLive) {
			super(sender);
			this.originalSender = sender;
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
			this.tiebreaker = new MersenneTwister(SmartVehicle.this.hashCode()
					+ parcel.hashCode()).nextLong();
			this.position = getRoadModel().getPosition(parcel);
			this.vanished = new LinkedList<Parcel>(vanishedDiscoveries);
		}

		public BidMessage(CommunicationUser sender, Parcel parcel, double bid,
				int timeToLive, Point position) {
			super(sender);
			this.originalSender = sender;
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
			this.tiebreaker = new MersenneTwister(SmartVehicle.this.hashCode()
					+ parcel.hashCode()).nextLong();
			this.position = position;
			this.vanished = new LinkedList<Parcel>(vanishedDiscoveries);
		}

		private BidMessage(CommunicationUser sender,
				CommunicationUser originalSender, Parcel parcel, double bid,
				int timeToLive, long tiebreaker, Point position,
				List<Parcel> vanished) {
			super(sender);
			this.originalSender = originalSender;
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
			this.tiebreaker = tiebreaker;
			this.position = position;
			this.vanished = vanished;
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

		public List<Parcel> getVanished() {
			return vanished;
		}

		public BidMessage copy(CommunicationUser newSender) {
			return new BidMessage(newSender, originalSender, parcel, bid, ttl,
					tiebreaker, position, vanished);
		}

	}

	public class BidStore {
		private final Map<Parcel, BidMessage> bids = new HashMap<Parcel, BidMessage>();
		private final LinkedList<BidMessage> queue = new LinkedList<BidMessage>();
		private final PriorityQueue<BidMessage> pqueue = new PriorityQueue<BidMessage>(
				11, new Comparator<BidMessage>() {

					@Override
					public int compare(BidMessage o1, BidMessage o2) {
						// Reverse ordering
						if (o1.getBid() > o2.getBid())
							return -1;
						else if (o1.getBid() < o2.getBid())
							return 1;
						else {
							PDPModel pm = getPDPModel();
							Point o1Location = o1.getPosition();
							Point o2Location = o2.getPosition();

							if (pm.containerContains(SmartVehicle.this,
									o1.getParcel()))
								o1Location = o1.getParcel().getDestination();
							if (pm.containerContains(SmartVehicle.this,
									o2.getParcel()))
								o2Location = o2.getParcel().getDestination();

							double o1Distance = Point.distance(getPosition(),
									o1Location);
							double o2Distance = Point.distance(getPosition(),
									o2Location);

							if (o1Distance < o2Distance)
								return -1;
							else if (o1Distance > o2Distance)
								return 1;
							else
								return 0;
						}
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

		public void purge(Parcel target) {
			purge(bids.get(target));
		}

		public Point position(Parcel parcel) {
			if (bids.containsKey(parcel))
				return bids.get(parcel).getPosition();
			return null;
		}
	}
}
