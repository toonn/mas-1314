package mas;

import static rinde.sim.core.model.pdp.PDPModel.ParcelState.ANNOUNCED;
import static rinde.sim.core.model.pdp.PDPModel.ParcelState.AVAILABLE;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
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
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
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
	private final BidStore commBids = new BidStore();
	private final Map<Parcel, BidMessage> ownBids = new HashMap<Parcel, BidMessage>();

	private int commCounter = 0;
	private final Map<SmartVehicle, Integer> commWith = new HashMap<SmartVehicle, Integer>();
	public static final String C_MALACHITE = "color.Malachite";
	public static final String C_PERIWINKLE = "color.Periwinkle";
	public static final String C_VERMILLION = "color.Vermillion";

	private final RandomGenerator rng = new MersenneTwister(123);
	private int numAgents = 0;
	private int numParcels = 0;
	private int direction = 0;

	private Optional<Parcel> curr = Optional.absent();
	private int TTL = 5;

	SmartVehicle(VehicleDTO dto) {
		super(dto);
	}

	@Override
	public void afterTick(TimeLapse timeLapse) {
		incrementCommWith();
		refreshCommWith();
		Iterator<Parcel> it = ownBids.keySet().iterator();
		while (it.hasNext()) {
			ParcelState state = getPDPModel().getParcelState(it.next());
			if (ANNOUNCED != state && AVAILABLE != state) {
				it.remove();
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

		Set<Parcel> parcelSet = new HashSet<Parcel>(getVisibleParcels(rm));

		parcelSet.addAll(ownBids.keySet());
		parcelSet.addAll(commBids.getParcels());

		for (Parcel parcel : parcelSet) {
			BidMessage bidMessage = new BidMessage(this, parcel, cost(pm, rm,
					parcel), TTL);
			ownBids.put(parcel, bidMessage);
			commBids.ensconce(bidMessage);
		}

		sendBid();

		// Select current obsession
		if (!curr.isPresent()) {
			curr = Optional.fromNullable(selectParcel(pm, rm));
		}

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
				if (rm.getPosition(this).equals(curr.get().getDestination())
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
					// pickup customer
					pm.pickup(this, curr.get(), time);
				}
			}
		} else if (pm.getParcels(ANNOUNCED, AVAILABLE).isEmpty()) {
			rm.moveTo(this, RoadModels.findClosestObject(rm.getPosition(this),
					rm, Depot.class), time);
		} else {
			int newNumAgents = RoadModels.findObjectsWithinRadius(
					getPosition(), rm, commRadius, SmartVehicle.class).size();
			int newNumParcels = RoadModels.findObjectsWithinRadius(
					getPosition(), rm, commRadius, Parcel.class).size();
			if ((newNumParcels - numParcels) < (newNumAgents - numAgents)) {
				direction = rng.nextInt();
			}
			numAgents = newNumAgents;
			numParcels = newNumParcels;

			Point destination = new Point(
					getPosition().x + Math.cos(direction), getPosition().y
							+ Math.sin(direction));
			rm.moveTo(this, destination, time);
			// TODO scale direction
		}
	}

	private double cost(PDPModel pm, RoadModel rm, Parcel parcel) {
		return 10;
	}

	private Collection<Parcel> getVisibleParcels(RoadModel rm) {
		return RoadModels.findObjectsWithinRadius(getPosition(), rm,
				commRadius, Parcel.class);
	}

	private Parcel selectParcel(PDPModel pm, RoadModel rm) {
		Parcel parcel = null;
		long parcelEndTime = Long.MAX_VALUE;
		for (BidMessage bid : commBids.senderMessages(this)) {
			boolean inCargo = pm.containerContains(this, bid.getParcel());
			if (!inCargo && !rm.containsObject(bid.getParcel())) {
				commBids.purge(bid);
			} else if (!inCargo
					&& bid.getParcel().getDeliveryTimeWindow().end < parcelEndTime) {
				parcel = bid.getParcel();
				parcelEndTime = parcel.getDeliveryTimeWindow().end;
			} else if (bid.getParcel().getPickupTimeWindow().end < parcelEndTime) {
				parcel = bid.getParcel();
				parcelEndTime = parcel.getPickupTimeWindow().end;
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
		if (RoadModels.findObjectsWithinRadius(getPosition(), getRoadModel(),
				commRadius, SmartVehicle.class).isEmpty())
			return;
		BidMessage bid = commBids.yoink();
		if (bid != null && cm != null) {
			cm.broadcast(bid);
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
			if (commWith.get(key) > 60 * 100) {
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

		public BidMessage(CommunicationUser sender, Parcel parcel, double bid,
				int timeToLive) {
			super(sender);
			this.parcel = parcel;
			this.bid = bid;
			this.ttl = timeToLive;
		}

		@Override
		public BidMessage clone() throws CloneNotSupportedException {
			return (BidMessage) super.clone();

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

	}

	private class BidStore {
		private final Map<Parcel, BidMessage> bids = new HashMap<Parcel, SmartVehicle.BidMessage>();
		private final LinkedList<BidMessage> queue = new LinkedList<SmartVehicle.BidMessage>();

		public void ensconce(BidMessage bidMessage) {
			Parcel parcel = bidMessage.getParcel();
			if (bids.containsKey(parcel)) {
				final BidMessage oldBid = bids.get(parcel);
				if (bidMessage.getBid() > oldBid.getBid()) {
					queue.remove(oldBid);
					queue.offer(bidMessage);
					bids.put(parcel, bidMessage);
				} else if (bidMessage.getBid() == oldBid.getBid()
						&& bidMessage.getTtl() > oldBid.getTtl()) {
					queue.set(queue.indexOf(oldBid), bidMessage);
					bids.put(parcel, bidMessage);
				}
			} else {
				queue.offer(bidMessage);
				bids.put(parcel, bidMessage);
			}
		}

		public Collection<Parcel> getParcels() {
			return bids.keySet();
		}

		public Set<BidMessage> senderMessages(CommunicationUser sender) {
			Set<BidMessage> sendersBids = new HashSet<SmartVehicle.BidMessage>();
			for (BidMessage bid : bids.values()) {
				if (bid.getSender() == sender) {
					sendersBids.add(bid);
				}
			}
			return sendersBids;
		}

		public BidMessage yoink() {
			BidMessage bid = queue.poll();
			if (bid == null)
				return null;
			bid.decrementTtl();
			if (bid.getTtl() > 0)
				queue.offer(bid);
			else
				bids.remove(bid.getParcel());
			try {
				return bid.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			return bid;
		}

		public void purge(BidMessage target) {
			bids.remove(target.getParcel());
			queue.remove(target);
		}
	}
}
