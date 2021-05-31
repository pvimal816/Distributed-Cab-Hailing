package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;


import java.io.*;
import java.util.ArrayList;

public class RideService extends AbstractBehavior<RideService.Command> {    
    public static final EntityTypeKey<Command> TypeKey = EntityTypeKey.create(RideService.Command.class, "RideServiceEntity");
    public static String input_file = "/Users/gokulnathpillai/Documents/GradSchool/PODS/Project 2/pods_project_02/IDs.txt";
    ArrayList<String> customerIds;

    public static ArrayList<String> readCustId(){
        try{
            File cabInfoFile = new File(input_file);
            BufferedReader br = new BufferedReader(new FileReader(cabInfoFile));
            String st;
        
            br.readLine();
        
            ArrayList<String> cabIds = new ArrayList<>();
            ArrayList<String> custIds = new ArrayList<>();
        
            while ((st = br.readLine()) != null && !st.equals("****"))
                cabIds.add(st);
        
            while ((st = br.readLine()) != null && !st.equals("****"))
                custIds.add(st);

            return custIds;
        } catch(IOException e){
            e.printStackTrace();
            return null;
        }
    }



    long fullFillRideActorCount=0L;

    private final ActorContext<Command> context;

    String rideServiceInstanceId;

    public RideService(String rideServiceInstanceId, ActorContext<Command> context) {
        super(context);
        customerIds = readCustId();
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

    public static final class RequestRide implements Command {
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

        // Create an array of valid cabIds here and check that requestRide.custId is present in them.
        // If not present, throw an error.

        if(!customerIds.contains(requestRide.custId)){
            System.err.println("[RideService.onRequestRide] Error, invalid customer id." + requestRide.custId);
            return rideService();
        }

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
