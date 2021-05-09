package pods.cabs;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.util.Objects;
import java.util.Random;

public class Cab {
    public enum CabState {
        AVAILABLE, COMMITTED,
        GIVING_RIDE, SIGNED_OUT
    }

    String cabId;
    /**
     * each cab maintains a reply id. All
     * the messages sent by this cab will contain
     * the unique monotonically increasing msgId.
      */
    long currentTimestamp;

    /**
     *  If the cab is interested
     *  in taking the next ride request
     * */
    boolean isInterested;
    CabState state;
    long lastKnownLocation;
    long rideCnt;

    /**
     *
     * All following fields stores
     * information about the ongoing ride.
     * */

    long rideId;
    long sourceLocation;
    long destinationLocation;
    ActorRef<FulfillRide.Command> fulFillRideActorRef;

    interface Command {}

    public static final class RequestRide implements Command {
        long rideId;
        long sourceLoc;
        long destinationLoc;
        ActorRef<FulfillRide.Command> replyTo;

        public RequestRide(long rideId, long sourceLoc, long destinationLoc,
                           ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }

        @Override
        public String toString() {
            return "RequestRide{" +
                    "rideId=" + rideId +
                    ", sourceLoc=" + sourceLoc +
                    ", destinationLoc=" + destinationLoc +
                    ", replyTo=" + replyTo +
                    '}';
        }
    }

    public static final class RideStarted implements Command {
        public RideStarted() {
        }
    }

    public static final class RideCanceled implements Command {
        public RideCanceled() {
        }
    }

    public final static class RideEnded implements Command {
        Long rideId;

        public RideEnded(Long rideId) {
            this.rideId = rideId;
        }
    }

    public static final class SignIn implements Command {
        long initialPos;

        public SignIn(long initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOut implements Command {
        public SignOut() {
        }
    }

    public static final class NumRides implements Command {
        ActorRef<NumRideResponse> replyTo;
        public NumRides(ActorRef<NumRideResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements Command {
        ActorRef<NumRideResponse> replyTo;
        public Reset(ActorRef<NumRideResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    interface CabResponse {}

    public static final class NumRideResponse implements CabResponse{
        long response;
        long timestamp;

        public NumRideResponse(long response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NumRideResponse that = (NumRideResponse) o;
            return response == that.response;
        }

        @Override
        public int hashCode() {
            return Objects.hash(response);
        }
    }

    public static final class RequestRideResponse implements FulfillRide.Command {
        boolean response;
        long timestamp;

        public RequestRideResponse(boolean response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
    }

    public Cab(String cabId) {
        this.cabId = cabId;
        this.currentTimestamp = 0;
    }

    public Behavior<Command> cab(){
        return Behaviors.receive(Command.class)
                .onMessage(RequestRide.class, this::onRequestRide)
                .onMessage(RideStarted.class, this::onRideStarted)
                .onMessage(RideCanceled.class, this::onRideCanceled)
                .onMessage(RideEnded.class, this::onRideEnded)
                .onMessage(SignIn.class, this::onSignIn)
                .onMessage(SignOut.class, this::onSignOut)
                .onMessage(Reset.class, this::onReset)
                .onMessage(NumRides.class, this::onNumRides)
                .build();
    }

    public static Behavior<Command> create(String cabId){
        return Behaviors.setup(
                ctx -> new Cab(cabId).cab());
    }

    public Behavior<Command> onRideStarted(RideStarted rideStarted){
        state = CabState.GIVING_RIDE;
        return cab();
    }

    public Behavior<Command> onRideCanceled(RideCanceled rideCanceled){
        state = CabState.AVAILABLE;
        return cab();
    }

    public Behavior<Command> onRideEnded(RideEnded rideEnded){
        if(rideId != rideEnded.rideId)
            return cab();
        state = CabState.AVAILABLE;
        lastKnownLocation = destinationLocation;
        rideCnt += 1;
        fulFillRideActorRef.tell(new FulfillRide.RideEnded(currentTimestamp++));
        return cab();
    }

    @Override
    public String toString() {
        return "Cab{" +
                "cabId='" + cabId + '\'' +
                ", currentTimestamp=" + currentTimestamp +
                ", isInterested=" + isInterested +
                ", state=" + state +
                ", lastKnownLocation=" + lastKnownLocation +
                ", rideCnt=" + rideCnt +
                ", rideId=" + rideId +
                ", sourceLocation=" + sourceLocation +
                ", destinationLocation=" + destinationLocation +
                ", fulFillRideActorRef=" + fulFillRideActorRef +
                '}';
    }

    public Behavior<Command> onRequestRide(RequestRide requestRide){

        System.err.println("[ Log ] Cab-"+ cabId +".onRequestRide: request received from " + requestRide.toString() + " and current state of cab is " + state.toString() +"\n\n");

        if(state!=CabState.AVAILABLE){
            requestRide.replyTo.tell(new RequestRideResponse(false, currentTimestamp++));
            return cab();
        }

        if(isInterested){
            isInterested = false;
            state = CabState.COMMITTED;
            sourceLocation = requestRide.sourceLoc;
            destinationLocation = requestRide.destinationLoc;
            rideId = requestRide.rideId;
            fulFillRideActorRef = requestRide.replyTo;
            requestRide.replyTo.tell(new RequestRideResponse(true, currentTimestamp++));
        }else{
            isInterested = true;
            requestRide.replyTo.tell(new RequestRideResponse(false, currentTimestamp++));
        }

        return cab();
    }

    public Behavior<Command> onSignIn(SignIn signIn){
        state = CabState.AVAILABLE;
        lastKnownLocation = signIn.initialPos;
        rideCnt = 0;
        isInterested = true;
        Random random = new Random();
        Globals.rideService.get(random.nextInt(Globals.rideService.size())).tell(
                new RideService.CabSignsIn(cabId, signIn.initialPos, currentTimestamp++)
        );
        return cab();
    }

    public Behavior<Command> onSignOut(SignOut signOut){
        state = CabState.SIGNED_OUT;
        Random random = new Random();
        Globals.rideService.get(random.nextInt(Globals.rideService.size())).tell(
                new RideService.CabSignsOut(cabId, currentTimestamp++)
        );
        return cab();
    }

    public Behavior<Command> onReset(Reset reset){
        if(state == CabState.GIVING_RIDE)
            onRideEnded(new RideEnded(rideId));
        if(state != CabState.SIGNED_OUT)
            onSignOut(new SignOut());
        reset.replyTo.tell(new NumRideResponse(rideCnt, currentTimestamp++));
        return cab();
    }

    public Behavior<Command> onNumRides(NumRides numRides){
        numRides.replyTo.tell(new NumRideResponse(rideCnt, currentTimestamp++));
        return cab();
    }

    //TODO: change the return values of handlers so that only
    // legal messages gets accepted in each state.

}
