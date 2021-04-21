package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import org.junit.ClassRule;
import org.junit.Test;

public class WalletTester {

    @ClassRule public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void getBalanceTest() {
        ActorRef<Wallet.WalletCommand> wallet = testKit.spawn(Wallet.create("101", 1000), "test_wallet");
        TestProbe<Wallet.ResponseBalance> probe = testKit.createTestProbe();
        wallet.tell(new Wallet.GetBalance(probe.ref()));
        probe.expectMessage(new Wallet.ResponseBalance(1000));
        testKit.stop(wallet);
    }

    @Test
    public void addBalanceTest(){
        ActorRef<Wallet.WalletCommand> wallet = testKit.spawn(Wallet.create("101", 1000), "test_wallet");
        TestProbe<Wallet.ResponseBalance> probe = testKit.createTestProbe();
        wallet.tell(new Wallet.AddBalance(100));
        wallet.tell(new Wallet.GetBalance(probe.ref()));
        probe.expectMessage(new Wallet.ResponseBalance(1100));
        testKit.stop(wallet);
    }

    @Test
    public void deductBalanceTest(){
        ActorRef<Wallet.WalletCommand> wallet = testKit.spawn(Wallet.create("101", 1000), "test_wallet");
        TestProbe<Wallet.ResponseBalance> probe = testKit.createTestProbe();
        wallet.tell(new Wallet.DeductBalance(100, probe.ref()));
        probe.expectMessage(new Wallet.ResponseBalance(900));
        testKit.stop(wallet);
    }

}
