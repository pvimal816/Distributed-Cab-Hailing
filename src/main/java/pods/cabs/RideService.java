package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

public class RideService extends AbstractBehavior<RideService.Command> {

    public static final EntityTypeKey<Command> TypeKey = EntityTypeKey.create(RideService.Command.class, "RideServiceEntity");

    long fullFillRideActorCount=0L;

    private final ActorContext<Command> context;

    String rideServiceInstanceId;

    public RideService(String rideServiceInstanceId, ActorContext<Command> context) {
        super(context);
        this.context = context;
        this.rideServiceInstanceId = rideServiceInstanceId;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RequestRide.class, this::onRequestRide)
                .build();
    }

    interface Command extends CborSerializable{}

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

        public RideResponse(long rideId, String cabId, long fare) {
            this.rideId = rideId;
            this.cabId = cabId;
            this.fare = fare;
        }
    }

    public Behavior<Command> rideService(){
        return Behaviors.receive(Command.class)
                .onMessage(RequestRide.class, this::onRequestRide)
                .build();
    }

    public Behavior<Command> onRequestRide(RequestRide requestRide){
        ActorRef<FulfillRide.Command> fulFillRideRef = context.spawn(FulfillRide.create(context.getSelf()),
                "fulfill_ride_actor_"+rideServiceInstanceId+"_"+(fullFillRideActorCount++)
        );

        fulFillRideRef.tell(new FulfillRide.RequestRide(
                        requestRide.sourceLoc, requestRide.destinationLoc,
                        requestRide.replyTo
                )
        );

        return rideService();
    }

    public static Behavior<Command> create(String rideServiceInstanceId) {
        System.err.println("[RideService.create] rideServiceInstanceId: " + rideServiceInstanceId);
        return Behaviors.setup(
                ctx -> new RideService(rideServiceInstanceId, ctx)
        );
    }
}
