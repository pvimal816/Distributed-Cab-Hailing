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
        testKit.stop(main);
    }

}
