package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static final String input_file = "F:\\academic docs\\MTech\\sem 2\\PODS\\projects\\akka_project\\cab_hailing\\IDs.txt";

    public static final class Create{
        public Create() {
        }
    }

    public static final class Started{
        public Started() {
        }

        @Override
        public boolean equals(Object o) {
            String className = o.getClass().getName();
            return (className.equals("pods.cabs.Main$Started"));
        }
    }

    ActorContext<Create> context;
    ActorRef<Started> replyTo;

    public Main(ActorContext<Create> context, ActorRef<Started> replyTo) {
        this.context = context;
        this.replyTo = replyTo;
    }

    public static Behavior<Create> create(ActorRef<Started> replyTo){
        return Behaviors.setup(ctx-> new Main(ctx, replyTo).main());
    }

    public Behavior<Create> main(){
        return Behaviors.receive(Create.class)
                .onMessage(Create.class, this::onCreate)
                .build();
    }

    public Behavior<Create> onCreate(Create create){
        Globals.rideService = new ArrayList<>();
        Globals.walletRefs = new HashMap<>();
        Globals.cabs = new HashMap<>();

        try {
            File cabInfoFile = new File(input_file);
            BufferedReader br = new BufferedReader(new FileReader(cabInfoFile));
            String st;

            //skip the first line containing "****"
            br.readLine();

            ArrayList<String> cabIds = new ArrayList<>();
            ArrayList<String> custIds = new ArrayList<>();

            while ((st = br.readLine()) != null && !st.equals("****"))
                cabIds.add(st);

            while ((st = br.readLine()) != null && !st.equals("****"))
                custIds.add(st);

            st = br.readLine();
            long balance = Long.parseLong(st);

            for (String cabId: cabIds) {
                Globals.cabs.put(cabId, context.spawn(Cab.create(cabId), "CabActor_"+cabId));
            }

            for (String custId: custIds) {
                Globals.walletRefs.put(custId, context.spawn(Wallet.create(custId, balance), "WalletActor_"+custId));
            }

            Map<String, RideService.CabInfo> cabInfos = new HashMap<>();
            for (String cabId: cabIds) {
                RideService.CabInfo cabInfo = new RideService.CabInfo(cabId, 0, Cab.CabState.SIGNED_OUT, -1);
                cabInfos.put(cabId, cabInfo);
            }

            for(int i=0; i<10; i++){
                Globals.rideService.add(context.spawn(RideService.create(i, cabInfos), "RideServiceActor_"+i));
            }

            //spawn one KVStore actor
            Map<String, Long> map = new HashMap<>();
            map.put(Globals.RIDE_ID_KEY, 0L);
            Globals.kvStoreRef = context.spawn(KVStore.create(map), "KVStoreActor");
        } catch (IOException e){
            e.printStackTrace();
        }

        replyTo.tell(new Started());
        return Behaviors.empty();
    }

}
