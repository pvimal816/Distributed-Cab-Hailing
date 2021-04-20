package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.util.Objects;

public class Wallet {
    long customerId;
    long balance;

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
        public final ActorRef<ResponseBalance> replyTo;

        public AddBalance(long amount, ActorRef<ResponseBalance> replyTo) {
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements WalletCommand {

        public Reset() {
        }
    }

    interface ResponseBalance {}



    public static final class GetBalanceResponse implements ResponseBalance {
        long balance;

        public GetBalanceResponse(long balance) {
            this.balance = balance;
        }

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if (!(o instanceof GetBalanceResponse)) return false;
            GetBalanceResponse getBalanceResponse = (GetBalanceResponse) o;
            return balance == getBalanceResponse.balance;
        }

        @Override
        public int hashCode() {
            return Objects.hash(balance);
        }
    }

    public static final class DeductBalanceResponse implements ResponseBalance {
        boolean result;

        public DeductBalanceResponse(boolean result) {
            this.result = result;
        }

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if (!(o instanceof DeductBalanceResponse)) return false;
            DeductBalanceResponse deductBalanceResponse = (DeductBalanceResponse) o;
            return result == deductBalanceResponse.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }

    }

    public static final class AddBalanceResponse implements ResponseBalance {
        boolean result;

        public AddBalanceResponse(boolean result) {
            this.result = result;
        }

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if (!(o instanceof AddBalanceResponse)) return false;
            AddBalanceResponse addBalanceResponse = (AddBalanceResponse) o;
            return result == addBalanceResponse.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }

    }

    private final ActorContext<WalletCommand> context;

    public static Behavior<WalletCommand> create(long customerId, long balance) {
        return Behaviors.setup(
                ctx -> new Wallet(ctx, customerId, balance).wallet());
    }

    public Wallet(ActorContext<WalletCommand> context, long customerId, long balance) {
        this.customerId = customerId;
        this.balance = balance;
        this.context = context;
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
        client.tell(new GetBalanceResponse(balance));
        return wallet();
    }

    private Behavior<WalletCommand> onDeductBalance(DeductBalance deductBalance) {
        ActorRef<ResponseBalance> client = deductBalance.replyTo;
        if (deductBalance.amount > 0 && balance >= deductBalance.amount) {
            client.tell(new DeductBalanceResponse(true));
            balance -= deductBalance.amount;
        } else {
            client.tell(new DeductBalanceResponse(false));
        }
        return wallet();
    }

    private Behavior<WalletCommand> onAddBalance(AddBalance addBalance) {
        ActorRef<ResponseBalance> client = addBalance.replyTo;
        if (addBalance.amount >= 0) {
            balance += addBalance.amount;
            client.tell(new AddBalanceResponse(true));
        } else {
            client.tell(new AddBalanceResponse(false));
        }
        return wallet();
    }

    private Behavior<WalletCommand> onReset(Reset reset) {
        balance = 0;
        return wallet();
    }

}
