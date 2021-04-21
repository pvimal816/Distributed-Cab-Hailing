package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.util.Objects;

public class Wallet {
    String customerId;
    long balance;
    final long initialBalance;

    interface WalletCommand {}
    // All the 'end points' of Wallet are will implement this interface.

    public static final class GetBalance implements WalletCommand {
        public final ActorRef<ResponseBalance> replyTo;

        public GetBalance(ActorRef<ResponseBalance> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class DeductBalance implements WalletCommand {
        public final long amount;
        public final ActorRef<ResponseBalance> replyTo;

        public DeductBalance(long amount, ActorRef<ResponseBalance> replyTo) {
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public static final class AddBalance implements WalletCommand {
        public final long amount;

        public AddBalance(long amount) {
            this.amount = amount;
        }
    }

    public static final class Reset implements WalletCommand {
        public final ActorRef<ResponseBalance> replyTo;

        public Reset(ActorRef<ResponseBalance> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static class ResponseBalance {
        long balance;
        public ResponseBalance(long balance) {this.balance = balance;}

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if (!(o instanceof ResponseBalance)) return false;
            ResponseBalance responseBalance = (ResponseBalance) o;
            return balance == responseBalance.balance;
        }

        @Override
        public int hashCode() {
            return Objects.hash(balance);
        }
    }

    private final ActorContext<WalletCommand> context;

    public static Behavior<WalletCommand> create(String customerId, long balance) {
        return Behaviors.setup(
                ctx -> new Wallet(ctx, customerId, balance).wallet());
    }

    public Wallet(ActorContext<WalletCommand> context, String customerId, long balance) {
        this.customerId = customerId;
        this.balance = balance;
        this.context = context;
        this.initialBalance = balance;
    }

    private Behavior<WalletCommand> wallet() {
        return Behaviors.receive(WalletCommand.class)
                .onMessage(GetBalance.class, this::onGetBalance)
                .onMessage(DeductBalance.class, this::onDeductBalance)
                .onMessage(AddBalance.class, this::onAddBalance)
                .onMessage(Reset.class, this::onReset)
                .build();
    }

    private Behavior<WalletCommand> onGetBalance(GetBalance getBalance) {
        ActorRef<ResponseBalance> client = getBalance.replyTo;
        client.tell(new ResponseBalance(balance));
        return wallet();
    }

    private Behavior<WalletCommand> onDeductBalance(DeductBalance deductBalance) {
        ActorRef<ResponseBalance> client = deductBalance.replyTo;
        if (deductBalance.amount > 0 && balance >= deductBalance.amount) {
            balance -= deductBalance.amount;
            client.tell(new ResponseBalance(balance));
        } else {
            client.tell(new ResponseBalance(-1));
        }
        return wallet();
    }

    private Behavior<WalletCommand> onAddBalance(AddBalance addBalance) {
        if (addBalance.amount >= 0)
            balance += addBalance.amount;
        return wallet();
    }

    private Behavior<WalletCommand> onReset(Reset reset) {
        ActorRef<ResponseBalance> client = reset.replyTo;
        balance = initialBalance;
        client.tell(new ResponseBalance(balance));
        return wallet();
    }

}
