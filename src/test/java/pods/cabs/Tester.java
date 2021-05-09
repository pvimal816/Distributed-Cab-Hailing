package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class Tester {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(
            ConfigFactory.parseString("akka {\n" +
                    "  loggers = [\"akka.event.slf4j.Slf4jLogger\"]\n" +
                    "  loglevel = \"DEBUG\"\n" +
                    "  logging-filter = \"akka.event.slf4j.Slf4jLoggingFilter\"\n" +
                    "}")
    );

    @Test
    public void requestRideTest1() {
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());

        Globals.cabs.get("101").tell(new Cab.SignIn(0));

        TestProbe<RideService.RideResponse> rideResponseProbe = testKit.createTestProbe();
        Globals.rideService.get(0).tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));

        rideResponseProbe.expectMessage(new RideService.RideResponse(0, "101", 1000, null, 0));

        testKit.stop(main);
    }

    @Test
    public void requestRideTest2() {
        /**
         * Three cabs signs in.
         * Then there comes three ride
         * requests at the same time.
          */
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());

        reset();

        Globals.cabs.get("101").tell(new Cab.SignIn(0));
        Globals.cabs.get("102").tell(new Cab.SignIn(0));
        Globals.cabs.get("103").tell(new Cab.SignIn(0));

        TestProbe<RideService.RideResponse> rideResponseProbe1 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe2 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe3 = testKit.createTestProbe();

        Globals.rideService.get(0).tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe1.ref()));
        Globals.rideService.get(0).tell(new RideService.RequestRide(
                "202", 50, 100, rideResponseProbe2.ref()));
        Globals.rideService.get(0).tell(new RideService.RequestRide(
                "203", 50, 100, rideResponseProbe3.ref()));

        RideService.RideResponse rideResponse = rideResponseProbe1.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe2.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe3.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        testKit.stop(main);
    }

    @Test
    public void cabTest1() {

        // One cab signs in. Then we request for a ride.
        // Then that ride gets ended and we again request for the ride.
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());


        ActorRef<Cab.Command> cab = Globals.cabs.get("101");
        ActorRef<RideService.Command> rideService = Globals.rideService.get(0);
        TestProbe<RideService.RideResponse> rideResponseProbe = testKit.createTestProbe();
        TestProbe<Cab.NumRideResponse> cabResponseProbe = testKit.createTestProbe();

        cab.tell(new Cab.SignIn(0));
        //This request should be accepted
        rideService.tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));
        RideService.RideResponse resp = rideResponseProbe.receiveMessage();
        assertNotEquals(resp.rideId, -1);

        cab.tell(new Cab.RideEnded(resp.rideId));

        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Again request for the ride.
        rideService.tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));
        //This ride request will be rejected because a cab gets tired after every alternate request.
        resp = rideResponseProbe.receiveMessage();
        assertEquals(resp.rideId, -1);

        //Yet, again request for the ride.
        rideService.tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));
        // This request should be entertained.
        resp = rideResponseProbe.receiveMessage();
        assertNotEquals(resp.rideId, -1);

        // Let's finish the ride.
        cab.tell(new Cab.RideEnded(resp.rideId));

        // Cab ride count should
        cab.tell(new Cab.NumRides(cabResponseProbe.getRef()));
        Cab.NumRideResponse cabResp = cabResponseProbe.receiveMessage();
        assertEquals(cabResp.response, 2);

        testKit.stop(main);
    }


    public void reset(){
        TestProbe<Cab.NumRideResponse> cabProbe = testKit.createTestProbe();
        for(Map.Entry<String, ActorRef<Cab.Command>> entry: Globals.cabs.entrySet()){
            entry.getValue().tell(new Cab.Reset(cabProbe.getRef()));
            Cab.NumRideResponse resetResponse = cabProbe.receiveMessage();
        }

        TestProbe<Wallet.ResponseBalance> walletProbe = testKit.createTestProbe();
        for(Map.Entry<String, ActorRef<Wallet.Command>> entry: Globals.walletRefs.entrySet()){
            entry.getValue().tell(new Wallet.Reset(walletProbe.getRef()));
            Wallet.ResponseBalance resetResponse = walletProbe.receiveMessage();
        }
    }

    @Test
    public void sampleTest1(){
        TestProbe<Main.Started> mainProbe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(mainProbe.ref()), "mainActor");
        main.tell(new Main.Create());
        mainProbe.expectMessage(new Main.Started());
        reset();

        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        cab101.tell(new Cab.SignIn(10));
        ActorRef<RideService.Command> rideService = Globals.rideService.get(0);
        // If we are going to raise multiple requests in this script,
        // better to send them to different RideService actors to achieve
        // load balancing.
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();
        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.ref()));
        RideService.RideResponse resp = probe.receiveMessage();
        // Blocks and waits for a response message.
        // There is also an option to block for a bounded period of time
        // and give up after timeout.
        assertNotEquals(-1, resp.rideId);
        cab101.tell(new Cab.RideEnded(resp.rideId));
    }

}
