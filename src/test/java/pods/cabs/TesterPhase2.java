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

        // This is used to initialise the cluster sharding of Cab and RideService.

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

        // Very basic test - cab 101's reset is checked, and then signs in , and checks that numRides = 0.

        System.out.println("\n");
        System.out.println("-------------------------------This is test 1.------------------------------");
        System.out.println("\n");


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
    public void cabTest2() {
        init();

        // Slightly more elaborate test.

        System.out.println("\n");
        System.out.println("--------------------------This is test 2.------------------------------");
        System.out.println("\n");


        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "101");
        TestProbe<Cab.NumRideResponse> resetRes = testKit.createTestProbe();

        cab101.tell(new Cab.Reset(resetRes.ref()));
        Cab.NumRideResponse resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        // Till this point, the Cab's reset function is checked.

        cab101.tell(new Cab.SignIn(100)); // the cab signs in.

        EntityRef<RideService.Command> rideService1 = rideSharding.entityRefFor(RideService.TypeKey, "rideService1");
        TestProbe<RideService.RideResponse> rideResponseTestProbe = testKit.createTestProbe();

        // Book ride.
        rideService1.tell(new RideService.RequestRide("201", 0L, 100L, rideResponseTestProbe.ref()));
        RideService.RideResponse rideResponse = rideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rideResponse.rideId);

        // Check if 101 has satisfied that ride or not - number of rides should be 1 at this point.
        cab101.tell(new Cab.NumRides(resetRes.ref()));
        resp  = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        // Book ride again - this should not be allocated as we have to reject alternate ride.
        rideService1.tell(new RideService.RequestRide("202", 100L, 200L, rideResponseTestProbe.ref()));
        rideResponse = rideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertEquals(-1, rideResponse.rideId);

        // Check if 101 has not satisfied this ride - it should still have 1 has number of rides.
        cab101.tell(new Cab.NumRides(resetRes.ref()));
        resp  = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);
    }

    @Test
    public void cabTest3() {
        init();

        // In this test, cab 101 signs in 

        System.out.println("\n");
        System.out.println("-------------------------This is test 3.----------------------------");
        System.out.println("\n");


        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "101");
        TestProbe<Cab.NumRideResponse> numRideResponseTestProbe = testKit.createTestProbe();

        cab101.tell(new Cab.NumRides(numRideResponseTestProbe.ref()));

        Cab.NumRideResponse resp  = numRideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);
    }

    @Test
    public void cabTest4() {
        init();

        // In this test, four cabs sign in at four different locations, and we send rideRequests to 4 different instances.

        
        System.out.println("\n");
        System.out.println("-----------------This is multi-cab-sign-in test.---------------------");
        System.out.println("\n");


        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "101");
        EntityRef<Cab.Command> cab102 = cabSharding.entityRefFor(Cab.TypeKey, "102");
        EntityRef<Cab.Command> cab103 = cabSharding.entityRefFor(Cab.TypeKey, "103");
        EntityRef<Cab.Command> cab104 = cabSharding.entityRefFor(Cab.TypeKey, "104");

        EntityRef<RideService.Command> rideService1 = rideSharding.entityRefFor(RideService.TypeKey, "rideService1");
        EntityRef<RideService.Command> rideService2 = rideSharding.entityRefFor(RideService.TypeKey, "rideService2");
        EntityRef<RideService.Command> rideService3 = rideSharding.entityRefFor(RideService.TypeKey, "rideService3");
        EntityRef<RideService.Command> rideService4 = rideSharding.entityRefFor(RideService.TypeKey, "rideService4");


        TestProbe<Cab.NumRideResponse> resetRes = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> reqTest = testKit.createTestProbe();

        cab101.tell(new Cab.Reset(resetRes.ref()));
        Cab.NumRideResponse resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        cab102.tell(new Cab.Reset(resetRes.ref()));
        resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        cab103.tell(new Cab.Reset(resetRes.ref()));
        resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        cab104.tell(new Cab.Reset(resetRes.ref()));
        resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        cab101.tell(new Cab.SignIn(100));
        cab102.tell(new Cab.SignIn(200));
        cab103.tell(new Cab.SignIn(300));
        cab104.tell(new Cab.SignIn(400));

        rideService1.tell(new RideService.RequestRide("201", 100, 200, reqTest.ref()));
        RideService.RideResponse rep = reqTest.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rep.rideId);
        assertEquals(1000, rep.fare);

        rideService2.tell(new RideService.RequestRide("202", 200, 300, reqTest.ref()));
        rep = reqTest.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rep.rideId);
        assertEquals(1000, rep.fare);

        rideService3.tell(new RideService.RequestRide("203", 300, 400, reqTest.ref()));
        rep = reqTest.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rep.rideId);
        assertEquals(1000, rep.fare);

        rideService4.tell(new RideService.RequestRide("204", 400, 500, reqTest.ref()));
        rep = reqTest.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rep.rideId);
        assertEquals(1000, rep.fare);
    }
   

    @Test
    public void cabTest5(){
        init();

        System.out.println("\n");
        System.out.println("-----------------This is test 5.---------------------");
        System.out.println("\n");


        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "101");
        EntityRef<RideService.Command> rideService1 = rideSharding.entityRefFor(RideService.TypeKey, "rideService1");

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

    @Test
    public void cabTest6() {
        init();

        // Slightly more elaborate test.

        System.out.println("\n");
        System.out.println("--------------------------This is test 6.------------------------------");
        System.out.println("\n");


        EntityRef<Cab.Command> cab101 = cabSharding.entityRefFor(Cab.TypeKey, "101");
        TestProbe<Cab.NumRideResponse> resetRes = testKit.createTestProbe();

        cab101.tell(new Cab.Reset(resetRes.ref()));
        Cab.NumRideResponse resp = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        // Till this point, the Cab's reset function is checked.

        cab101.tell(new Cab.SignIn(100)); // the cab signs in.

        EntityRef<RideService.Command> rideService1 = rideSharding.entityRefFor(RideService.TypeKey, "rideService1");
        TestProbe<RideService.RideResponse> rideResponseTestProbe = testKit.createTestProbe();

        // Book ride.
        rideService1.tell(new RideService.RequestRide("201", 0L, 100L, rideResponseTestProbe.ref()));
        RideService.RideResponse rideResponseOne = rideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rideResponseOne.rideId);

        // Check if 101 has satisfied that ride or not - number of rides should be 1 at this point.
        cab101.tell(new Cab.NumRides(resetRes.ref()));
        resp  = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        // Book ride again - this should not be allocated as we have to reject alternate ride.
        rideService1.tell(new RideService.RequestRide("202", 200L, 300L, rideResponseTestProbe.ref()));
        RideService.RideResponse rideResponseTwo = rideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertEquals(-1, rideResponseTwo.rideId);

        // Check if 101 has not satisfied this ride - it should still have 1 has number of rides.
        cab101.tell(new Cab.NumRides(resetRes.ref()));
        resp  = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(1, resp.response);

        // End the first ride that was started.
        cab101.tell(new Cab.RideEnded(rideResponseOne.rideId));

        // Book ride again - this should not be allocated as we have to reject alternate ride.
        rideService1.tell(new RideService.RequestRide("203", 200L, 300L, rideResponseTestProbe.ref()));
        RideService.RideResponse rideResponseThree = rideResponseTestProbe.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rideResponseThree.rideId);

        // Check if 101 has satisfied the ride or not - numRides should be two now.
        cab101.tell(new Cab.NumRides(resetRes.ref()));
        resp  = resetRes.receiveMessage(Duration.ofSeconds(10));
        assertEquals(2, resp.response);
    }

}
