package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.typesafe.config.ConfigFactory;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

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

        Globals.cabRefs.get("101").tell(new Cab.SignIn(0));

        TestProbe<RideService.RideResponse> rideResponseProbe = testKit.createTestProbe();
        Globals.rideServiceRefs.get((long)0).tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));

        rideResponseProbe.expectMessage(new RideService.RideResponse(0, "101", 1000, null, 0));

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


        ActorRef<Cab.CabCommand> cab = Globals.cabRefs.get("101");
        ActorRef<RideService.RideServiceCommand> rideService = Globals.rideServiceRefs.get((long)0);
        TestProbe<RideService.RideResponse> rideResponseProbe = testKit.createTestProbe();
        TestProbe<Cab.NumRideResponse> probe1 = testKit.createTestProbe();

        cab.tell(new Cab.SignIn(0));

        //This request should be accepted
        rideService.tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));
        rideResponseProbe.expectMessage(new RideService.RideResponse(0, "101", 1000, null, 0));
        cab.tell(new Cab.RideEnded());

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
        rideResponseProbe.expectMessage(new RideService.RideResponse(0, "0", 0, null, 0));

        //Yet, again request for the ride.
        rideService.tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe.ref()));
        // This request should be entertained.
        rideResponseProbe.expectMessage(new RideService.RideResponse(0, "101", 1000, null, 0));

        // Let's finish the ride.
        cab.tell(new Cab.RideEnded());

        // Cab ride count should
        cab.tell(new Cab.NumRides(probe1.getRef()));
        probe1.expectMessage(new Cab.NumRideResponse(2, 0));

        testKit.stop(main);
    }

}
