package pods.cabs;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import java.util.Arrays;

import akka.cluster.sharding.typed.javadsl.Entity;
import akka.persistence.typed.PersistenceId;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Main {
    public static void main(String[] args) {

        if (args.length == 0)
            System.err.println("Please provide the port number!");
        else{
            Arrays.stream(args).map(Integer::parseInt).forEach(Main::startup);
        }       
    }

    private static Behavior<Void> rootBehavior() {
        return Behaviors.setup(context -> Behaviors.empty());
    }

    private static void startup(int port) {


        Map<String, Object> overrides = new HashMap<>();
        overrides.put("akka.remote.artery.canonical.port", port);
        if (port == 25251) {
            overrides.put("akka.persistence.journal.plugin", "akka.persistence.journal.leveldb");
            overrides.put("akka.persistence.journal.proxy.start-target-journal", "on");
        }
        else  {
            overrides.put("akka.persistence.journal.plugin", "akka.persistence.journal.proxy");
        }

        Config config = ConfigFactory.parseMap(overrides)
                .withFallback(ConfigFactory.load());

        // Create an Akka system
        ActorSystem<Void> system = ActorSystem.create(rootBehavior(), "ClusterSystem", config);

        final ClusterSharding cabSharding = ClusterSharding.get(system);

        //initialize sharding for cabs
        cabSharding.init(
                Entity.of(Cab.TypeKey,
                        entityContext ->
                                Cab.create(
                                        entityContext.getEntityId(),
                                        PersistenceId.of(
                                                entityContext.getEntityTypeKey().name(), entityContext.getEntityId()
                                        )
                                )
                )
        );

        final ClusterSharding rideServiceSharding = ClusterSharding.get(system);
        rideServiceSharding.init(
                Entity.of(RideService.TypeKey,
                        entityContext ->
                                RideService.create(entityContext.getEntityId())
                )
        );

        if (port == 25251) {
            cabSharding.entityRefFor(Cab.TypeKey, "101");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService1");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService2");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService3");
        } else if (port == 25252) {
            cabSharding.entityRefFor(Cab.TypeKey, "102");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService4");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService5");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService6");
        } else if (port == 25253) {
            cabSharding.entityRefFor(Cab.TypeKey, "103");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService7");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService8");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService9");
        } else if (port == 25254) {
            cabSharding.entityRefFor(Cab.TypeKey, "104");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService10");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService11");
            rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService12");
        }
    }
}
