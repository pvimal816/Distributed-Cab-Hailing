package pods.cabs;


import akka.actor.typed.ActorRef;

import java.util.ArrayList;
import java.util.Map;

public class Globals {
    public static Map<String, ActorRef<Cab.CabCommand>> cabRefs;
    public static Map<String, ActorRef<Wallet.WalletCommand>> walletRefs;
    public static Map<Long, ActorRef<RideService.RideServiceCommand>> rideServiceRefs;
}
