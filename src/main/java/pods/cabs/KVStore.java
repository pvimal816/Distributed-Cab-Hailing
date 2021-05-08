package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.util.Map;

public class KVStore {

    Map<String, Long> map;

    interface Command{}

    public static final class GetAndIncrement implements Command {
        String key;
        ActorRef<FulfillRide.Command> replyTo;

        public GetAndIncrement(String key, ActorRef<FulfillRide.Command> replyTo) {
            this.key = key;
            this.replyTo = replyTo;
        }
    }

    public static final class KVStoreResponse implements FulfillRide.Command {
        String key;
        Long value;

        public KVStoreResponse(String key, Long value) {
            this.key = key;
            this.value = value;
        }
    }

    public KVStore(Map<String, Long> map) {
        this.map = map;
    }

    public static Behavior<Command> create(Map<String, Long> map){
        return Behaviors.setup(
                ctx -> new KVStore(map).kvstore()
        );
    }

    public Behavior<Command> kvstore(){
        return Behaviors.receive(Command.class)
                .onMessage(GetAndIncrement.class, this::onGet)
                .build();
    }

    public Behavior<Command> onGet(GetAndIncrement getAndIncrement){
        Long val = map.get(getAndIncrement.key);
        if(val==null)
            val=0L;
        getAndIncrement.replyTo.tell(new KVStoreResponse(getAndIncrement.key, val));
        ++val;
        map.put(getAndIncrement.key, val);
        return kvstore();
    }
}
