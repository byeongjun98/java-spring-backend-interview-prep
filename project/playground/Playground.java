// 01-oop-design-patterns.md 실습용. 빌드 도구 없이 javac/java로 바로 실행.
// 실행: javac Playground.java && java Playground

interface PaymentMethod {
    void pay(int amount);
}

class CardPayment implements PaymentMethod {
    public void pay(int amount) {
        System.out.println("[카드 결제] " + amount + "원 결제");
    }
}

class PointPayment implements PaymentMethod {
    public void pay(int amount) {
        System.out.println("[포인트 결제] " + amount + "원 차감");
    }
}

// checkout()은 PaymentMethod 인터페이스에만 의존 (DIP) — 구현체가 뭔지 몰라도 됨
class Checkout {
    void checkout(PaymentMethod method, int amount) {
        method.pay(amount);
    }
}

public class Playground {
    public static void main(String[] args) {
        Checkout checkout = new Checkout();

        // 런타임에 어떤 결제수단을 넣을지 결정 (Strategy 패턴)
        checkout.checkout(new CardPayment(), 10000);
        checkout.checkout(new PointPayment(), 3000);

        // 실험: 여기 새 결제수단(예: BankTransferPayment)을 만들어서 넣어보기
        // Checkout 클래스는 한 줄도 안 고쳐도 되는지 확인 (OCP 확인)
    }
}
