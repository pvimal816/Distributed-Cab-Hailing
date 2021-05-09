package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class RideService {

    long fullFillRideActorCount=0L;
    public static final class CabInfo {
        String cabId;
        long lastKnownLocation;
        Cab.CabState state;
        long timestamp;

        public CabInfo(String cabId, long lastKnownLocation,
                       Cab.CabState state, long timestamp) {
            this.cabId = cabId;
            this.lastKnownLocation = lastKnownLocation;
            this.state = state;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "CabInfo{" +
                    "cabId='" + cabId + '\'' +
                    ", lastKnownLocation=" + lastKnownLocation +
                    ", state=" + state +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    Map<String, CabInfo> cabInfos;
    private final ActorContext<Command> context;

    long rideServiceInstanceId;

    public RideService(long rideServiceInstanceId, Map<String, CabInfo> cabInfos, ActorContext<Command> context) {
        this.cabInfos = cabInfos;
        this.context = context;
        this.rideServiceInstanceId = rideServiceInstanceId;
    }

    interface Command {}

    public static final class CabSignsIn implements Command {
        String cabId;
        long initialPos;
        long timestamp;

        public CabSignsIn(String cabId, long initialPos, long timestamp) {
            this.cabId = cabId;
            this.initialPos = initialPos;
            this.timestamp = timestamp;
        }
    }

    public static final class CabSignsOut  implements Command {
        String cabId;
        long timestamp;

        public CabSignsOut(String cabId, long timestamp) {
            this.cabId = cabId;
            this.timestamp = timestamp;
        }
    }

    public static final class RequestRide  implements Command {
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

    public static final class RideResponse implements Command {
        long rideId;
        String cabId;
        long fare;
        ActorRef<FulfillRide.Command> fRide;
        long timestamp;

        public RideResponse(long rideId, String cabId, long fare, ActorRef<FulfillRide.Command> fRide, long timestamp) {
            this.rideId = rideId;
            this.cabId = cabId;
            this.fare = fare;
            this.fRide = fRide;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RideResponse that = (RideResponse) o;
            return fare == that.fare && cabId.equals(that.cabId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cabId, fare);
        }
    }

    public static final class RideEnded implements Command {
        String cabId;
        long currentLocation;
        long timestamp;

        public RideEnded(String cabId, long timestamp, long currentLocation) {
            this.cabId = cabId;
            this.timestamp = timestamp;
            this.currentLocation = currentLocation;
        }
    }

    public static final class CabInfoUpdate implements Command {
        CabInfo cabInfo;
        
        public CabInfoUpdate(CabInfo cabInfo) {
            this.cabInfo = cabInfo;
        }
    }

    public void broadCastCabInfo(CabInfo cabInfo){
        for (int i = 0; i<Globals.rideService.size(); i++) {
            if(i==rideServiceInstanceId)
                continue;
            ActorRef<Command> ref = Globals.rideService.get(i);
            ref.tell(new CabInfoUpdate(cabInfo));
        }
    }

    public Behavior<Command> rideService(){
        return Behaviors.receive(Command.class)
                .onMessage(CabSignsIn.class, this::onCabSignsIn)
                .onMessage(CabSignsOut.class, this::onCabSignsOut)
                .onMessage(RequestRide.class, this::onRequestRide)
                .onMessage(RideResponse.class, this::onRideResponse)
                .onMessage(RideEnded.class, this::onRideEnded)
                .onMessage(CabInfoUpdate.class, this::onCabInfoUpdate)
                .build();
    }

    public Behavior<Command> onCabSignsIn(CabSignsIn cabSignsIn){
        CabInfo cabInfo = cabInfos.get(cabSignsIn.cabId);
        // ignore if this is an obsolete message
        if(cabInfo.timestamp > cabSignsIn.timestamp)
            return rideService();

        //update local state
        cabInfo.state = Cab.CabState.AVAILABLE;
        cabInfo.lastKnownLocation = cabSignsIn.initialPos;
        cabInfo.timestamp = cabSignsIn.timestamp;
        cabInfos.put(cabInfo.cabId, cabInfo);

        // share local state to other instances
        broadCastCabInfo(cabInfo);

        return rideService();
    }

    public Behavior<Command> onCabSignsOut(CabSignsOut cabSignsOut){
        CabInfo cabInfo = cabInfos.get(cabSignsOut.cabId);
        // ignore if this is an obsolete message
        if(cabInfo.timestamp > cabSignsOut.timestamp)
            return rideService();

        //update local state
        cabInfo.state = Cab.CabState.SIGNED_OUT;
        cabInfo.timestamp = cabSignsOut.timestamp;
        cabInfos.put(cabInfo.cabId, cabInfo);

        // share local state to other instances
        broadCastCabInfo(cabInfo);

        return rideService();
    }

    public Behavior<Command> onRequestRide(RequestRide requestRide){

        System.err.println("[ Log ] RideService.onRequestRide: cache state is " + cabInfos.toString() + "\n\n");

        ActorRef<FulfillRide.Command> fulFillRideRef = context.spawn(FulfillRide.create(
                new ArrayList<>(cabInfos.values()), context.getSelf()),
                "fulfill_ride_actor_"+rideServiceInstanceId+"_"+(fullFillRideActorCount++)
        );

        fulFillRideRef.tell(new FulfillRide.RequestRide(
                        requestRide.sourceLoc, requestRide.destinationLoc,
                        requestRide.custId, requestRide.replyTo
                )
        );

        return rideService();
    }

    public Behavior<Command> onRideResponse(RideResponse rideResponse){
        if(rideResponse.rideId == -1)
            return rideService();

        CabInfo cabInfo = cabInfos.get(rideResponse.cabId);

        // check if the message is obsolete
        if(rideResponse.timestamp < cabInfo.timestamp)
            return rideService();

        cabInfo.timestamp = rideResponse.timestamp;
        cabInfo.state = Cab.CabState.GIVING_RIDE;

        cabInfos.put(cabInfo.cabId, cabInfo);

        broadCastCabInfo(cabInfo);

        return rideService();
    }

    public Behavior<Command> onRideEnded(RideEnded rideEnded){
        CabInfo cabInfo = cabInfos.get(rideEnded.cabId);
        if(cabInfo.timestamp > rideEnded.timestamp)
            return rideService();
        cabInfo.lastKnownLocation = rideEnded.currentLocation;
        cabInfo.state = Cab.CabState.AVAILABLE;
        cabInfo.timestamp = rideEnded.timestamp;
        return rideService();
    }

    public Behavior<Command> onCabInfoUpdate(CabInfoUpdate cabInfoUpdate){
        CabInfo localCabInfo = cabInfos.get(cabInfoUpdate.cabInfo.cabId);
        //update only if this message is the latest message.
        if(localCabInfo.timestamp > cabInfoUpdate.cabInfo.timestamp)
            return rideService();
        cabInfos.put(cabInfoUpdate.cabInfo.cabId, cabInfoUpdate.cabInfo);
        return rideService();
    }

    public static Behavior<Command> create(
            long rideServiceInstanceId, Map<String, RideService.CabInfo> cabInfos) {
        return Behaviors.setup(
                ctx -> new RideService(rideServiceInstanceId, cabInfos, ctx).rideService()
        );
    }
}
