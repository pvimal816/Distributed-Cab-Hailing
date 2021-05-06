package pods.cabs;

import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import pods.cabs.Cab.CabState;

import pods.cabs.RideService.CabInfo;

import java.util.ArrayList;
import java.util.Comparator;

public class FulfillRide {

    /*
    * This actor will be created by
    * a rideService actor and will be
    * given a cached table containing
    * info about cabs.
    *
    * Table must contain "cabId, lastKnownLocation, state"
    * for all the cabs.
    *
    * */



    ArrayList<CabInfo> cabInfos;
    ArrayList<CabInfo> nearestCabs;
    long sourceLoc;
    long destinationLoc;
    long rideId;
    String custId;
    long fare;
    String chosenCabId;
    long chosenCabReplyId;

    ActorRef<RideService.RideServiceCommand> parentRef;
    ActorRef<RideService.RideResponse> replyTo;

    ActorContext<Command> context;
//    ActorContext<Wallet.ResponseBalance>

    interface Command {}

    public FulfillRide(ActorContext<Command> context, ArrayList<CabInfo> cabInfos,
                       ActorRef<RideService.RideServiceCommand> parentRef) {
        this.context = context;
        this.cabInfos = cabInfos;
        this.parentRef = parentRef;
    }

    public final static class RequestRide implements Command {
        long sourceLoc;
        long destinationLoc;
        long rideId;
        String custId;

        ActorRef<RideService.RideResponse> replyTo;

        public RequestRide(long sourceLoc, long destinationLoc, long rideId, String custId,
                           ActorRef<RideService.RideResponse> replyTo) {
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.rideId = rideId;
            this.replyTo = replyTo;
            this.custId = custId;
        }
    }

    public static final class RideEnded implements FulfillRide.Command {
        long cabReplyId;
        public RideEnded(long cabReplyId) {
            this.cabReplyId = cabReplyId;
        }
    }

    public static Behavior<Command> create(ArrayList<CabInfo> cabInfos, ActorRef<RideService.RideServiceCommand> parentRef){
        return Behaviors.setup(
                ctx -> new FulfillRide(ctx, cabInfos, parentRef).fulFillRide());
    }

    private Behavior<Command> fulFillRide() {
        return Behaviors.receive(Command.class)
                .onMessage(RequestRide.class, this::onRequestRide)
                .onMessage(RideEnded.class, this::onRideEnded)
                .onMessage(Wallet.ResponseBalance.class, this::onResponseBalance)
                .onMessage(Cab.RequestRideResponse.class, this::onRequestRideResponse)
                .build();
    }

    public Behavior<Command> onRequestRide(RequestRide requestRide){
        this.sourceLoc = requestRide.sourceLoc;
        this.destinationLoc = requestRide.destinationLoc;
        this.rideId = requestRide.rideId;
        this.custId = requestRide.custId;
        cabInfos.sort(Comparator.comparingLong(cabInfo -> Math.abs(cabInfo.lastKnownLocation - sourceLoc)));
        cabInfos.removeIf(cabInfo -> cabInfo.state!=CabState.AVAILABLE);
        replyTo = requestRide.replyTo;
        if(cabInfos.size()==0){
            replyTo.tell(new RideService.RideResponse(-1, "0", 0, null, -1));
            return Behaviors.stopped();
        }
        // copy upto three nearest cabs into nearestCabs
        nearestCabs.add(cabInfos.get(0));
        if(cabInfos.size()>1) nearestCabs.add(cabInfos.get(1));
        if(cabInfos.size()>2) nearestCabs.add(cabInfos.get(2));

        ActorRef<Cab.CabCommand> cab = Globals.cabRefs.get(nearestCabs.get(0).cabId);
        cab.tell(new Cab.RequestRide(rideId, sourceLoc, destinationLoc, context.getSelf()));
        return fulFillRide();
    }

    public Behavior<Command> onRequestRideResponse(Cab.RequestRideResponse requestRideResponse){
        if(requestRideResponse.response){
            CabInfo cabInfo = nearestCabs.get(0);
            chosenCabId = cabInfo.cabId;
            fare = Math.abs(cabInfo.lastKnownLocation - sourceLoc) * 10;
            chosenCabReplyId = requestRideResponse.cabReplyId;
            Globals.walletRefs.get(custId).tell(new Wallet.DeductBalance(fare, context.getSelf()));
        } else {
            nearestCabs.remove(0);
            if(nearestCabs.isEmpty()){
                replyTo.tell(new RideService.RideResponse(-1, "0", 0, null, -1));
                return Behaviors.stopped();
            }
            ActorRef<Cab.CabCommand> cab = Globals.cabRefs.get(nearestCabs.get(0).cabId);
            cab.tell(new Cab.RequestRide(rideId, sourceLoc, destinationLoc, context.getSelf()));
        }
        return fulFillRide();
    }

    public Behavior<Command> onResponseBalance(Wallet.ResponseBalance responseBalance){
        if(responseBalance.balance == -1) {
            // not enough balance in wallet
            // notify cab to cancel the ride
            Globals.cabRefs.get(chosenCabId).tell(new Cab.RideCanceled());
            // notify rideService instance of failure
            replyTo.tell(new RideService.RideResponse(-1, "0", 0, null, -1));
            return Behaviors.stopped();
        }

        // notify cab to start the ride
        Globals.cabRefs.get(chosenCabId).tell(new Cab.RideStarted());
        // notify request maker of success
        replyTo.tell(new RideService.RideResponse(rideId, chosenCabId, fare, context.getSelf(), chosenCabReplyId));
        // notify ride service of success so that it can update the state of the cab in it's cache
        parentRef.tell(new RideService.RideResponse(rideId, chosenCabId, fare, context.getSelf(), chosenCabReplyId));

        return fulFillRide();
    }

    public Behavior<Command> onRideEnded(RideEnded rideEnded){
        parentRef.tell(new RideService.RideEnded(chosenCabId, rideEnded.cabReplyId));
        return Behaviors.stopped();
    }

}
