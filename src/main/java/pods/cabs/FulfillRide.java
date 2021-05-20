package pods.cabs;

import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;

import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;

import java.util.ArrayList;
import java.util.Comparator;

public class FulfillRide {

    /*
    * This actor will be created by
    * a rideService actor.
    * */

    long sourceLoc;
    long destinationLoc;
    long rideId;
    long fare;
    String chosenCabId;

    long nextCabToTry = 201;
    ClusterSharding sharding;

    ActorRef<RideService.RideResponse> replyTo;

    ActorContext<Command> context;

    interface Command {}

    public FulfillRide(ActorContext<Command> context) {
        this.context = context;
        sharding = ClusterSharding.get(this.context.getSystem());
    }

    public final static class RequestRide implements Command {
        long sourceLoc;
        long destinationLoc;
        ActorRef<RideService.RideResponse> replyTo;

        public RequestRide(long sourceLoc, long destinationLoc,
                           ActorRef<RideService.RideResponse> replyTo) {
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    public static Behavior<Command> create(ActorRef<RideService.Command> parentRef){
        return Behaviors.setup(
                ctx -> new FulfillRide(ctx).fulFillRide());
    }

    private Behavior<Command> fulFillRide() {
        return Behaviors.receive(Command.class)
                .onMessage(RequestRide.class, this::onRequestRide)
                .onMessage(Cab.RequestRideResponse.class, this::onRequestRideResponse)
                .onMessage(KVStore.KVStoreResponse.class, this::onKVStoreResponse)
                .build();
    }

    public Behavior<Command> onRequestRide(RequestRide requestRide){
        this.sourceLoc = requestRide.sourceLoc;
        this.destinationLoc = requestRide.destinationLoc;
        replyTo = requestRide.replyTo;
        // generate a new rideId;
        EntityRef<KVStore.Command> kvStoreRef = sharding.entityRefFor(KVStore.TypeKey, "kvstore1");
        kvStoreRef.tell(new KVStore.GetAndIncrement("rideId", context.getSelf()));
        return fulFillRide();
    }

    public Behavior<Command> onKVStoreResponse(KVStore.KVStoreResponse kvStoreResponse){
        sharding.init(
                Entity.of(Cab.TypeKey, entityContext -> Cab.create(entityContext.getEntityId(), PersistenceId.of(
                        entityContext.getEntityTypeKey().name(), entityContext.getEntityId()
                )))
        );

        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "" + nextCabToTry);

        rideId = kvStoreResponse.value;
        cab.tell(new Cab.RequestRide(rideId, sourceLoc, destinationLoc, context.getSelf()));

        return fulFillRide();
    }

    public Behavior<Command> onRequestRideResponse(Cab.RequestRideResponse requestRideResponse){
        if(requestRideResponse.response){
            chosenCabId = "" + nextCabToTry;
            fare = (Math.abs(requestRideResponse.lastKnownLocation - sourceLoc) +
                    Math.abs(destinationLoc - sourceLoc)) * 10;
            replyTo.tell(new RideService.RideResponse(rideId, chosenCabId, fare));
        } else {
            ++nextCabToTry;
            if(nextCabToTry > 204){
                replyTo.tell(new RideService.RideResponse(-1, "0", 0));
                return Behaviors.stopped();
            }
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "" + nextCabToTry);
            cab.tell(new Cab.RequestRide(rideId, sourceLoc, destinationLoc, context.getSelf()));
        }
        return fulFillRide();
    }
}
