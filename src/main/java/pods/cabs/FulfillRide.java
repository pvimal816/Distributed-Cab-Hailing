package pods.cabs;

import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import pods.cabs.Cab.CabState;

import java.lang.reflect.Array;
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

    public static final class CabInfo {
        String cabId;
        long lastKnownLocation;
        CabState state;

        public CabInfo(String cabId, long lastKnownLocation,
                       CabState state) {
            this.cabId = cabId;
            this.lastKnownLocation = lastKnownLocation;
            this.state = state;
        }
    }

    ArrayList<CabInfo> cabInfos;
    ArrayList<CabInfo> nearestCabs;
    long sourceLoc;
    long destinationLoc;
    long rideId;
    String custId;

    ActorRef<RideService.RideResponse> replyTo;

    ActorContext<Command> context;
    ActorContext<Wallet.ResponseBalance>

    interface Command {}


    public FulfillRide(ActorContext<Command> context, ArrayList<CabInfo> cabInfos) {
        this.context = context;
        this.cabInfos = cabInfos;
    }

    public final static class RequestRide implements Command {
        long sourceLoc;
        long destinationLoc;
        long rideId;
        String custId;
        ActorRef<RideService.RideResponse> replyTo;

        public RequestRide(long sourceLoc, long destinationLoc, long rideId, String custId
                           ActorRef<RideService.RideResponse> replyTo) {
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.rideId = rideId;
            this.replyTo = replyTo;
            this.custId = custId;
        }
    }

    public static final class RequestRideResponse implements FulfillRide.Command {
        boolean response;

        public RequestRideResponse(boolean response) {
            this.response = response;
        }
    }

    public final static class RideEnded implements Command{
    }

    public static Behavior<Command> create(ArrayList<CabInfo> cabInfos){
        return Behaviors.setup(
                ctx -> new FulfillRide(ctx, cabInfos).fulFillRide());
    }

    private Behavior<Command> fulFillRide() {
        return Behaviors.receive(Command.class)
                .onMessage(RequestRide.class, this::onRequestRide)
                .onMessage(RideEnded.class, this::onRideEnded)
                .onMessage(RequestRideResponse.class, this::onRequestRideResponse)
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
            replyTo.tell(new RideService.RideResponse(-1, 0, 0, null));
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

    public Behavior<Command> onRequestRideResponse(RequestRideResponse requestRideResponse){
        if(requestRideResponse.response){
          //TODO: try to deduct balance
            CabInfo cabInfo = nearestCabs.get(0);
            long fare = Math.abs(cabInfo.lastKnownLocation - sourceLoc) * 10;
            Globals.walletRefs.get(custId).tell(new Wallet.DeductBalance(fare, context.getSelf()));

        }
        return fulFillRide();
    }

    public Behavior<Command> onRideEnded(RideEnded rideEnded){

        return fulFillRide();
    }

}
