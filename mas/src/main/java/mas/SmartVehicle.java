package mas;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.communication.CommunicationAPI;
import rinde.sim.core.model.communication.CommunicationUser;
import rinde.sim.core.model.communication.Message;
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
class SmartVehicle extends DefaultVehicle implements CommunicationUser {

	private CommunicationAPI cm;
	private double commRadius = 1;
	private double commReliability = 0.8;
	private final BidStore commBids = new BidStore();
	private final Map<Parcel, BidMessage> ownBids = new HashMap<Parcel, BidMessage>();

	private int commCounter = 0;
	private final Map<CommunicationUser, Integer> commWith = new HashMap<CommunicationUser, Integer>();
	public static final String C_MALACHITE = "color.Malachite";
	public static final String C_PERIWINKLE = "color.Periwinkle";
	public static final String C_VERMILLION = "color.Vermillion";

	private Optional<Parcel> curr = Optional.absent();

	SmartVehicle(VehicleDTO dto) {
		super(dto);
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

		// Select current obsession
		if (!curr.isPresent()) {
			curr = Optional.fromNullable(RoadModels.findClosestObject(
					rm.getPosition(this), rm, Parcel.class));
		}

		// Deal with current obsession
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

	private void sendBids(long currentTime) {
		if (lastCommunication + COMMUNICATION_PERIOD < currentTime) {
			lastCommunication = currentTime;
			if (cm != null) {
				cm.broadcast(new Message(this) {
				});
			}
		}
	}

	private void incrementCommWith() {
		Iterator<CommunicationUser> it = commWith.keySet().iterator();
		while (it.hasNext()) {
			CommunicationUser key = it.next();
			commWith.put(key, commWith.get(key) + 1);

		}
	}

	private void refreshCommWith() {
		Iterator<CommunicationUser> it = commWith.keySet().iterator();
		while (it.hasNext()) {
			CommunicationUser key = it.next();
			if (commWith.get(key) > 60 * 100) {
				it.remove();
			}
		}
	}

	public String getNoReceived() {
		return "" + commCounter;
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

		public BidMessage yoink() {
			BidMessage bid = queue.poll();
			bid.decrementTtl();
			if (bid.getTtl() > 0)
				queue.offer(bid);
			try {
				return bid.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			return bid;
		}
	}

}
