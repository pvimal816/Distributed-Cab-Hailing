package pods.cabs;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;

import java.util.Objects;
import java.util.Random;

public class Cab extends EventSourcedBehavior<Cab.Command, Cab.Event, Cab.State> {

    public static final EntityTypeKey<Command> TypeKey = EntityTypeKey.create(Cab.Command.class, "CabEntity");

    interface Command extends CborSerializable{}

    interface Event extends CborSerializable{}

    static final class State implements CborSerializable{
        public enum CabState {
            AVAILABLE,
            GIVING_RIDE, SIGNED_OUT
        }

        String cabId;

        /**
         *  If the cab is interested
         *  in taking the next ride request
         * */
        public boolean isInterested;
        public CabState state;
        public long lastKnownLocation;
        public long rideCnt;

        /**
         *
         * All following fields stores
         * information about the ongoing ride.
         * */
        public long rideId;
        public long sourceLocation;
        public long destinationLocation;
        public long fare;

        public State(String cabId, boolean isInterested, CabState state, long lastKnownLocation) {
            this.cabId = cabId;
            this.isInterested = isInterested;
            this.state = state;
            this.lastKnownLocation = lastKnownLocation;
            this.rideCnt = 0;
        }
    }

    @Override
    public State emptyState() {
        return new State(this.persistenceId().id().split("\\|")[1],
                true, State.CabState.SIGNED_OUT, 0);
    }

    public static final class RequestRideEvent implements Event{
        long rideId;
        long sourceLoc;
        long destinationLoc;

        public RequestRideEvent(long rideId, long sourceLoc, long destinationLoc) {
            this.rideId = rideId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
        }
    }

    public static final class RideEndedEvent implements Event{
        Long rideId;

        public RideEndedEvent(Long rideId) {
            this.rideId = rideId;
        }
    }

    public static final class SignInEvent implements Event{
        long initialPos;

        public SignInEvent(long initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOutEvent implements Event{
        int dummy=0;
    }

    public static final class ResetEvent implements Event{
        int dummy=0;
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(RequestRideEvent.class, Cab::onRequestRideEvent)
                .onEvent(RideEndedEvent.class, Cab::onRideEndedEvent)
                .onEvent(SignInEvent.class, Cab::onSignInEvent)
                .onEvent(SignOutEvent.class, Cab::onSignOutEvent)
                .onEvent(ResetEvent.class, Cab::onResetEvent)
                .build();
    }

    public static State onRequestRideEvent(State state, RequestRideEvent event){
        if(state.state!=State.CabState.AVAILABLE){
            throw new IllegalStateException("Account balance can't be negative");
        }

        if(state.isInterested){
            state.isInterested = false;
            state.state = State.CabState.GIVING_RIDE;
            state.sourceLocation = event.sourceLoc;
            state.destinationLocation = event.destinationLoc;
            state.rideId = event.rideId;
        }else{
            state.isInterested = true;
        }

        return state;
    }

    public static State onRideEndedEvent(State state, RideEndedEvent event){
        if(state.rideId != event.rideId)
            throw new IllegalStateException("No such ride to end!");
        state.state = State.CabState.AVAILABLE;
        state.lastKnownLocation = state.destinationLocation;
        state.rideCnt += 1;
        return state;
    }

    public static State onSignInEvent(State state, SignInEvent event){
        state.state = State.CabState.AVAILABLE;
        state.lastKnownLocation = event.initialPos;
        state.rideCnt = 0;
        state.isInterested = true;
        return state;
    }

    public static State onSignOutEvent(State state, SignOutEvent event){
        state.state = State.CabState.SIGNED_OUT;
        return state;
    }

    public static State onResetEvent(State state, ResetEvent event){
        state.state = State.CabState.SIGNED_OUT;
        return state;
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder().forStateType(State.class)
                .onCommand(RequestRide.class, this::onRequestRide)
                .onCommand(RideEnded.class, this::onRideEnded)
                .onCommand(SignIn.class, this::onSignIn)
                .onCommand(SignOut.class, this::onSignOut)
                .onCommand(Reset.class, this::onReset)
                .onCommand(NumRides.class, this::onNumRides)
                .build();
    }

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

        public NumRideResponse(long response) {
            this.response = response;
        }
    }

    public static final class RequestRideResponse implements FulfillRide.Command {
        boolean response;
        long lastKnownLocation;
        public RequestRideResponse(boolean response, long lastKnownLocation) {
            this.response = response;
            this.lastKnownLocation = lastKnownLocation;
        }
    }

    public static Behavior<Command> create(String cabId, PersistenceId persistenceId){
        return Behaviors.setup(
                ctx -> new Cab(ctx, persistenceId)
        );
    }

    public Cab(ActorContext<Command> context, PersistenceId persistenceId) {
        super(persistenceId);
    }

    public Effect<Event, State> onRideEnded(State state, RideEnded rideEnded){
        if(state.rideId != rideEnded.rideId)
            return Effect().none();
        return Effect()
                .persist(new RideEndedEvent(rideEnded.rideId));
    }

    private Effect<Event, State> onRequestRide(State state, RequestRide requestRide){
        if(state.state==State.CabState.AVAILABLE && state.isInterested) {
            return Effect().persist(new RequestRideEvent(requestRide.rideId, requestRide.sourceLoc, requestRide.destinationLoc))
                    .thenRun(newState -> requestRide.replyTo.tell(
                            new RequestRideResponse(true, state.lastKnownLocation)
                            )
                    );
        }else{
            return Effect().none().thenRun(
                    newState -> requestRide.replyTo.tell(new RequestRideResponse(false, -1))
            );
        }
    }

    public Effect<Event, State> onSignIn(State state, SignIn signIn){
        if(state.state != State.CabState.SIGNED_OUT)
            return Effect().none();
        return Effect().persist(
            new SignInEvent(signIn.initialPos)
        );
    }

    public Effect<Event, State> onSignOut(State state, SignOut signOut){
        if(state.state != State.CabState.AVAILABLE)
            return Effect().none();
        return Effect().persist(
                new SignOutEvent()
        );
    }

    public Effect<Event, State> onReset(Reset reset){
        return Effect()
                .persist(new ResetEvent())
                .thenRun(newState -> reset.replyTo.tell(new NumRideResponse(1)));
    }

    public Effect<Event, State> onNumRides(NumRides numRides){
        return Effect()
                .none()
                .thenRun(newState-> numRides.replyTo.tell(new NumRideResponse(newState.rideCnt)));
    }
}
