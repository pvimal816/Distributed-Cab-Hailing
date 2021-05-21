package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TesterPhase2 {
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

    @Test
    public void cabTest1() {
        Cluster cluster = Cluster.get(testKit.system());
        cluster.manager().tell(Join.create(cluster.selfMember().address()));

        ClusterSharding cabSharding = ClusterSharding.get(testKit.system());
        cabSharding.init(Entity.of(Cab.TypeKey,
                entityContext -> Cab.create(entityContext.getEntityId(),
                        PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId()
                        )))
        );

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

}
