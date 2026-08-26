// // 나쁜 예: 필드 직접 노출, 어디서든 잘못된 값을 넣을 수 있음
// public class Account {
//     public int balance;
// }
// account.balance = -1000; // 막을 방법이 없음

// 좋은 예: 상태 변경을 메서드로만 허용, 불변식(invariant)을 클래스가 지킴
public class Account {
    private int balance;

    public void withdraw(int amount) {
        if (amount > balance)
            throw new IllegalStateException("잔액 부족");
        balance -= amount;
    }
}