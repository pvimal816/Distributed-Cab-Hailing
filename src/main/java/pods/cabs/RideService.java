package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.util.ArrayList;
import java.util.Map;

public class RideService {

    public static final class CabInfo {
        String cabId;
        long lastKnownLocation;
        Cab.CabState state;
        long lastCabReplyId;

        public CabInfo(String cabId, long lastKnownLocation,
                       Cab.CabState state, long lastCabReplyId) {
            this.cabId = cabId;
            this.lastKnownLocation = lastKnownLocation;
            this.state = state;
            this.lastCabReplyId = lastCabReplyId;
        }
    }

    Map<String, CabInfo> cabInfos;
    private final ActorContext<RideServiceCommand> context;
    long rideId = 0;

    public RideService(Map<String, CabInfo> cabInfos, ActorContext<RideServiceCommand> context) {
        this.cabInfos = cabInfos;
        this.context = context;
    }

    interface RideServiceCommand {}

    public static final class CabSignsIn implements RideServiceCommand{
        String cabId;
        long initialPos;
        long cabReplyId;

        public CabSignsIn(String cabId, long initialPos, long cabReplyId) {
            this.cabId = cabId;
            this.initialPos = initialPos;
            this.cabReplyId = cabReplyId;
        }
    }

    public static final class CabSignsOut  implements RideServiceCommand {
        String cabId;
        long cabReplyId;

        public CabSignsOut(String cabId, long cabReplyId) {
            this.cabId = cabId;
            this.cabReplyId = cabReplyId;
        }
    }

    public static final class RequestRide  implements RideServiceCommand {
        String custId;
        long sourceLoc;
        long destinationLoc;
        ActorRef<RideResponse> replyTo;

        public RequestRide(String custId, long sourceLoc, long destinationLoc, ActorRef<RideResponse> replyTo) {
            this.custId = custId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    public static final class RideResponse implements RideServiceCommand{
        long rideId;
        String cabId;
        long fare;
        ActorRef<FulfillRide.Command> fRide;
        long cabReplyId;

        public RideResponse(long rideId, String cabId, long fare, ActorRef<FulfillRide.Command> fRide, long cabReplyId) {
            this.rideId = rideId;
            this.cabId = cabId;
            this.fare = fare;
            this.fRide = fRide;
            this.cabReplyId = cabReplyId;
        }
    }

    public static final class RideEnded implements RideServiceCommand {
        String cabId;
        long cabReplyId;

        public RideEnded(String cabId, long cabReplyId) {
            this.cabId = cabId;
            this.cabReplyId = cabReplyId;
        }
    }

    public void broadCastCabInfo(CabInfo cabInfo){
        //TODO: implement this
    }

    public Behavior<RideServiceCommand> rideService(){
        return Behaviors.receive(RideServiceCommand.class)
                .onMessage(CabSignsIn.class, this::onCabSignsIn)
                .onMessage(CabSignsOut.class, this::onCabSignsOut)
                .onMessage(RequestRide.class, this::onRequestRide)
                .build();
    }

    public Behavior<RideServiceCommand> onCabSignsIn(CabSignsIn cabSignsIn){
        CabInfo cabInfo = cabInfos.get(cabSignsIn.cabId);
        // ignore if this is an obsolete message
        if(cabInfo.lastCabReplyId > cabSignsIn.cabReplyId)
            return rideService();

        //update local state
        cabInfo.state = Cab.CabState.AVAILABLE;
        cabInfo.lastKnownLocation = cabSignsIn.initialPos;
        cabInfo.lastCabReplyId = cabSignsIn.cabReplyId;
        cabInfos.put(cabInfo.cabId, cabInfo);

        // share local state to other instances
        broadCastCabInfo(cabInfo);

        return rideService();
    }

    public Behavior<RideServiceCommand> onCabSignsOut(CabSignsOut cabSignsOut){
        CabInfo cabInfo = cabInfos.get(cabSignsOut.cabId);
        // ignore if this is an obsolete message
        if(cabInfo.lastCabReplyId > cabSignsOut.cabReplyId)
            return rideService();

        //update local state
        cabInfo.state = Cab.CabState.SIGNED_OUT;
        cabInfo.lastCabReplyId = cabSignsOut.cabReplyId;
        cabInfos.put(cabInfo.cabId, cabInfo);

        // share local state to other instances
        broadCastCabInfo(cabInfo);

        return rideService();
    }

    public Behavior<RideServiceCommand> onRequestRide(RequestRide requestRide){
        ActorRef<FulfillRide.Command> fulFillRideRef = context.spawn(FulfillRide.create(
                new ArrayList<>(cabInfos.values()), context.getSelf()),
                "fulfill_ride_actor" + rideId
        );

        //TODO: Currently the rideId is not globally consistent. Fix it.

        fulFillRideRef.tell(new FulfillRide.RequestRide(
                        requestRide.sourceLoc, requestRide.destinationLoc,
                        rideId++, requestRide.custId, requestRide.replyTo
                )
        );

        return rideService();
    }

    public Behavior<RideServiceCommand> onRideResponse(RideResponse rideResponse){
        //TODO: implement this
        if(rideResponse.rideId == -1)
            return rideService();

        CabInfo cabInfo = cabInfos.get(rideResponse.cabId);

        // check if the message is obsolete
        if(rideResponse.cabReplyId < cabInfo.lastCabReplyId)
            return rideService();

        cabInfo.lastCabReplyId = rideResponse.cabReplyId;
        cabInfo.state = Cab.CabState.GIVING_RIDE;

        cabInfos.put(cabInfo.cabId, cabInfo);

        broadCastCabInfo(cabInfo);

        return rideService();
    }

    public Behavior<RideServiceCommand> onRideEnded(RideEnded rideEnded){
        //TODO: implement this
        return rideService();
    }

    //TODO: implement a new message type CabInfoUpdate and corresponding handler function onCabInfoUpdate

}
