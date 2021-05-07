package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

public class Tester {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

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

}
