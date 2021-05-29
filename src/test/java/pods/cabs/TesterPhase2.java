package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.persistence.typed.PersistenceId;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.*;

public class TesterPhase2 {

    static ClusterSharding cabSharding;
    static ClusterSharding rideSharding;

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(
            ConfigFactory.parseString("akka {\n" +
                    "  loggers = [\"akka.event.slf4j.Slf4jLogger\"]\n" +
                    "  loglevel = \"DEBUG\"\n" +
                    "  logging-filter = \"akka.event.slf4j.Slf4jLoggingFilter\"\n" +
                    "  actor.provider=\"cluster\"\n" +
                    "  actor.allow-java-serialization = on\n" +
                    "  remote.artery.canonical.hostname = \"127.0.0.1\"\n" +
                    "  remote.artery.canonical.port = 0\n" +
                    "  cluster.seed-nodes = [\"akka://ClusterSystem@127.0.0.1:25251\", \"akka://ClusterSystem@127.0.0.1:25252\"]\n" +
                    "  cluster.downing-provider-class= \"akka.cluster.sbr.SplitBrainResolverProvider\"\n" +
                    "  persistence.journal.plugin=\"akka.persistence.journal.proxy\"\n" +
                    "  persistence.journal.proxy.target-journal-plugin=\"akka.persistence.journal.leveldb\"\n" +
                    "  persistence.journal.proxy.target-journal-address = \"akka://ClusterSystem@127.0.0.1:25251\"\n" +
                    "  persistence.journal.proxy.start-target-journal = \"off\"\n" +
                    "}")
    );

    public static void init() {
        Cluster cluster = Cluster.get(testKit.system());
        cluster.manager().tell(Join.create(cluster.selfMember().address()));

        cabSharding = ClusterSharding.get(testKit.system());
        cabSharding.init(Entity.of(Cab.TypeKey,
                entityContext -> Cab.create(entityContext.getEntityId(),
                        PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId()
                        )))
        );

        rideSharding = ClusterSharding.get(testKit.system());
        rideSharding.init(Entity.of(RideService.TypeKey,
                entityContext -> RideService.create(entityContext.getEntityId())
        ));
    }

    @Test
    public void cabTest1() {
        init();

        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "cab101");
        TestProbe<Cab.NumRideResponse> resetRes = testKit.createTestProbe();

        cab101.tell(new Cab.Reset(resetRes.ref()));

        Cab.NumRideResponse resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        cab101.tell(new Cab.SignIn(100));
        cab101.tell(new Cab.NumRides(resetRes.ref()));

        resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(0, resp.response);
    }

    @Test
    public void cabTest2_1() {
        init();

        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "cab101");
        TestProbe<Cab.NumRideResponse> resetRes = testKit.createTestProbe();

        cab101.tell(new Cab.Reset(resetRes.ref()));
        Cab.NumRideResponse resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        cab101.tell(new Cab.SignIn(100));

        EntityRef<RideService.Command> rideService1 = rideSharding.entityRefFor(RideService.TypeKey, "rideService1");
        TestProbe<RideService.RideResponse> rideResponseTestProbe = testKit.createTestProbe();
        rideService1.tell(new RideService.RequestRide("201", 0L, 100L, rideResponseTestProbe.ref()));

        RideService.RideResponse rideResponse = rideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rideResponse.rideId);

        cab101.tell(new Cab.NumRides(resetRes.ref()));

        resp  = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);
    }

    @Test
    public void cabTest2_2() {
        init();

        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "cab101");
        TestProbe<Cab.NumRideResponse> numRideResponseTestProbe = testKit.createTestProbe();

        cab101.tell(new Cab.NumRides(numRideResponseTestProbe.ref()));

        Cab.NumRideResponse resp  = numRideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);
    }

    @Test
    public void cabTest2(){
        Cluster cluster = Cluster.get(testKit.system());
        cluster.manager().tell(Join.create(cluster.selfMember().address()));
        // Enabling cab sharding
        ClusterSharding cabSharding = ClusterSharding.get(testKit.system());
        cabSharding.init(Entity.of(Cab.TypeKey,
                entityContext -> Cab.create(entityContext.getEntityId(),
                        PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId()
                        )))
        );
        // Enabling ride service sharding
        ClusterSharding rideServiceSharding = ClusterSharding.get(testKit.system());
        rideServiceSharding.init(
                Entity.of(RideService.TypeKey,
                        entityContext ->
                                RideService.create(entityContext.getEntityId())
                )
        );
        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "cab101");
        EntityRef<RideService.Command> rideService1 = rideServiceSharding.entityRefFor(RideService.TypeKey, "rideService1");
        TestProbe<Cab.NumRideResponse> resetRes = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> reqTest = testKit.createTestProbe();
        cab101.tell(new Cab.Reset(resetRes.ref()));
        Cab.NumRideResponse resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);
        cab101.tell(new Cab.SignIn(100));
        rideService1.tell(new RideService.RequestRide("201", 100, 200, reqTest.ref()));
        RideService.RideResponse rep = reqTest.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rep.rideId);
        assertEquals(1000, rep.fare);
    }

}
