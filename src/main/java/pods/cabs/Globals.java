package pods.cabs;


import akka.actor.typed.ActorRef;

import java.util.ArrayList;
import java.util.Map;

public class Globals {
    public static Map<String, ActorRef<Cab.Command>> cabs;
    public static Map<String, ActorRef<Wallet.Command>> walletRefs;
//    public static Map<Long, ActorRef<RideService.Command>> rideServiceRefs;
    public static ArrayList<ActorRef<RideService.Command>> rideService;
    public static ActorRef<KVStore.Command> kvStoreRef;
    public static final String RIDE_ID_KEY = "ride_id";
}
