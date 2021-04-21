package pods.cabs;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

public class Cab {
    public enum CabState {
        AVAILABLE, COMMITTED,
        GIVING_RIDE, SIGNED_OUT;
    }

    String cabId;
    /* If the cab is interested
     *  in taking the next ride request
     * */
    boolean isInterested;
    CabState state;
    long lastKnownLocation;

    /*
     * All following fields stores
     * information about ongoing ride.
     * */

    long rideId;
    long sourceLocation;
    long destinationLocation;
    long rideCnt;
    ActorRef<FulfillRide.Command> fulFillRideActorRef;

    interface CabCommand {}

    public static final class RequestRide implements CabCommand {
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
    }

    public static final class RideStarted implements CabCommand {
        public RideStarted() {
        }
    }

    public static final class RideCanceled implements CabCommand {
        public RideCanceled() {
        }
    }

    public static final class RideEnded implements CabCommand {
        public RideEnded() {
        }
    }

    public static final class SignIn implements CabCommand {
        long initialPos;

        public SignIn(long initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOut implements CabCommand {
        public SignOut() {
        }
    }

    public static final class NumRides implements CabCommand {
        ActorRef<NumRideResponse> replyTo;
        public NumRides(ActorRef<NumRideResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements CabCommand {
        ActorRef<NumRideResponse> replyTo;
        public Reset(ActorRef<NumRideResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    interface CabResponse {}

    public static final class NumRideResponse implements CabResponse{
        long response;

        public NumRideResponse(long response) {
            this.response = response;
        }
    }



    public Cab(String cabId) {
        this.cabId = cabId;
    }

    public Behavior<Cab.CabCommand> cab(){
        return Behaviors.receive(CabCommand.class)
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

    public static Behavior<Cab.CabCommand> create(String cabId){
        return Behaviors.setup(
                ctx -> new Cab(cabId).cab());
    }

    public Behavior<Cab.CabCommand> onRideStarted(RideStarted rideStarted){
        state = CabState.GIVING_RIDE;
        return cab();
    }

    public Behavior<Cab.CabCommand> onRideCanceled(RideCanceled rideCanceled){
        state = CabState.AVAILABLE;
        return cab();
    }

    public Behavior<Cab.CabCommand> onRideEnded(RideEnded rideEnded){
        state = CabState.AVAILABLE;
        lastKnownLocation = destinationLocation;
        rideCnt += 1;
        fulFillRideActorRef.tell(new FulfillRide.RideEnded());
        return cab();
    }

    public Behavior<Cab.CabCommand> onRequestRide(RequestRide requestRide){
        if(state == CabState.AVAILABLE && isInterested){
            isInterested = false;
            state = CabState.COMMITTED;
            sourceLocation = requestRide.sourceLoc;
            destinationLocation = requestRide.destinationLoc;
            rideId = requestRide.rideId;
            fulFillRideActorRef = requestRide.replyTo;
            requestRide.replyTo.tell(new FulfillRide.RequestRideResponse(true));
        }
        requestRide.replyTo.tell(new FulfillRide.RequestRideResponse(false));
        return cab();
    }

    public Behavior<Cab.CabCommand> onSignIn(SignIn signIn){
        state = CabState.AVAILABLE;
        lastKnownLocation = signIn.initialPos;
        rideCnt = 0;
        isInterested = true;
        return cab();
    }

    public Behavior<Cab.CabCommand> onSignOut(SignOut signOut){
        state = CabState.SIGNED_OUT;
        return cab();
    }

    public Behavior<Cab.CabCommand> onReset(Reset reset){
        reset.replyTo.tell(new NumRideResponse(rideCnt));
        if(state == CabState.GIVING_RIDE)
            onRideEnded(new RideEnded());
        if(state != CabState.SIGNED_OUT)
            onSignOut(new SignOut());
        return cab();
    }

    public Behavior<Cab.CabCommand> onNumRides(NumRides numRides){
        numRides.replyTo.tell(new NumRideResponse(rideCnt));
        return cab();
    }

}
