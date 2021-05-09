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
    public void cabTest1() {

        // One cab signs in. Then we request for a ride.
        // Then that ride gets ended and we again request for the ride.
        final TestKitJunitResource testKit = new TestKitJunitResource(
                ConfigFactory.parseString("akka {\n" +
                        "  loggers = [\"akka.event.slf4j.Slf4jLogger\"]\n" +
                        "  loglevel = \"DEBUG\"\n" +
                        "  logging-filter = \"akka.event.slf4j.Slf4jLoggingFilter\"\n" +
                        "}")
        );
        TestProbe<Main.Started> probe = testKit.createTestProbe();

        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "cabTest1mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());

        reset();

        ActorRef<Cab.Command> cab = Globals.cabs.get("101");
        ActorRef<RideService.Command> rideService = Globals.rideService.get(0);
        TestProbe<RideService.RideResponse> rideResponseProbe = testKit.createTestProbe();
        TestProbe<Cab.NumRideResponse> cabResponseProbe = testKit.createTestProbe();

        cab.tell(new Cab.SignIn(0));

        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Now, This request should be accepted
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
        ActorRef<Main.Create> main = testKit.spawn(Main.create(mainProbe.ref()), "sampleTest1mainActor");
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

        testKit.stop(main);
    }

    @Test
    public void getBalanceTest() {
        ActorRef<Wallet.Command> wallet = testKit.spawn(Wallet.create("101", 1000), "getBalanceTestWallet");
        TestProbe<Wallet.ResponseBalance> probe = testKit.createTestProbe();
        wallet.tell(new Wallet.GetBalance(probe.ref()));
        probe.expectMessage(new Wallet.ResponseBalance(1000));
        testKit.stop(wallet);
    }

    @Test
    public void addBalanceTest(){
        ActorRef<Wallet.Command> wallet = testKit.spawn(Wallet.create("101", 1000), "addBalanceTestWallet");
        TestProbe<Wallet.ResponseBalance> probe = testKit.createTestProbe();
        wallet.tell(new Wallet.AddBalance(100));
        wallet.tell(new Wallet.GetBalance(probe.ref()));
        probe.expectMessage(new Wallet.ResponseBalance(1100));
        testKit.stop(wallet);
    }

    @Test
    public void deductBalanceTest(){
        ActorRef<Wallet.Command> wallet = testKit.spawn(Wallet.create("101", 1000), "deductBalanceTestWallet");
        TestProbe<FulfillRide.Command> probe = testKit.createTestProbe();
        wallet.tell(new Wallet.DeductBalance(100, probe.ref()));
        probe.expectMessage(new Wallet.ResponseBalance(900));
        testKit.stop(wallet);
    }


    @Test
    public void requestRideTest1() {
        /**
         * very simple test to check basic functionality:
         * cab 101 signs in. Then there comes a ride request
         * which should get entertained.
         */
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "requestRideTest1mainActor");
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
         * Three cabs signs in. Then there comes three ride
         * requests at the same time. All three request should be
         * satisfied.
         */
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "requestRideTest2mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());

        reset();

        Globals.cabs.get("101").tell(new Cab.SignIn(0));
        Globals.cabs.get("102").tell(new Cab.SignIn(0));
        Globals.cabs.get("103").tell(new Cab.SignIn(0));

        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
    public void requestRideTest3() {

        /**
         * There are 4 cabs signed in and 4 passengers.
         * First 3 rides are nearest to all the 4 passengers.
         * But, 4th one is very far away from all 4.
         *
         * Then all 4 passengers tries to book a cab.
         * But it might happen that the nearest 3 cab
         * selected by some customer have already been
         * booked. So, this test will fail occasionally.
         *
         * That is just because of eventual but not strong
         * consistency.
         */
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "requestRideTest3mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());

        reset();

        Globals.cabs.get("101").tell(new Cab.SignIn(50));
        Globals.cabs.get("102").tell(new Cab.SignIn(50));
        Globals.cabs.get("103").tell(new Cab.SignIn(50));
        Globals.cabs.get("104").tell(new Cab.SignIn(0));

        TestProbe<RideService.RideResponse> rideResponseProbe1 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe2 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe3 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe4 = testKit.createTestProbe();

        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Globals.rideService.get(0).tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe1.ref()));
        Globals.rideService.get(4).tell(new RideService.RequestRide(
                "202", 50, 100, rideResponseProbe2.ref()));
        Globals.rideService.get(9).tell(new RideService.RequestRide(
                "203", 50, 100, rideResponseProbe3.ref()));
        Globals.rideService.get(5).tell(new RideService.RequestRide(
                "203", 50, 100, rideResponseProbe4.ref()));


        RideService.RideResponse rideResponse = rideResponseProbe1.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe2.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe3.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe4.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rideResponse.rideId);

        testKit.stop(main);
    }

    @Test
    public void requestRideTest4() {

        /**
         * Same test as requestRideTest3. But here we
         * wait for 1 sec after each request ride.
         * That is to make sure that each rideService
         * replica has latest information.
         *
         * This test passes with high probability.
         */
        TestProbe<Main.Started> probe = testKit.createTestProbe();
        ActorRef<Main.Create> main = testKit.spawn(Main.create(probe.ref()), "requestRideTest4mainActor");
        main.tell(new Main.Create());
        probe.expectMessage(new Main.Started());

        reset();

        Globals.cabs.get("101").tell(new Cab.SignIn(50));
        Globals.cabs.get("102").tell(new Cab.SignIn(50));
        Globals.cabs.get("103").tell(new Cab.SignIn(50));
        Globals.cabs.get("104").tell(new Cab.SignIn(0));

        TestProbe<RideService.RideResponse> rideResponseProbe1 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe2 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe3 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> rideResponseProbe4 = testKit.createTestProbe();


        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Globals.rideService.get(0).tell(new RideService.RequestRide(
                "201", 50, 100, rideResponseProbe1.ref()));

        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Globals.rideService.get(4).tell(new RideService.RequestRide(
                "202", 50, 100, rideResponseProbe2.ref()));

        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Globals.rideService.get(9).tell(new RideService.RequestRide(
                "203", 50, 100, rideResponseProbe3.ref()));


        // wait for all caches to be updated
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Globals.rideService.get(5).tell(new RideService.RequestRide(
                "203", 50, 100, rideResponseProbe4.ref()));



        RideService.RideResponse rideResponse = rideResponseProbe1.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe2.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe3.receiveMessage();
        assertNotEquals(-1, rideResponse.rideId);

        rideResponse = rideResponseProbe4.receiveMessage(Duration.ofSeconds(10));
        assertNotEquals(-1, rideResponse.rideId);

        testKit.stop(main);
    }

}
