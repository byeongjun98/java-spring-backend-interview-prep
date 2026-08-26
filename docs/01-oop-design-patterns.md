# 01. OOP 원칙 + 디자인패턴 실전 적용

공고 인용: 자격요건 "OOP 등 기본 CS 지식", 우대사항 "디자인패턴을 이해하여 코드에 적용할 수 있고 아키텍쳐 설계에 능숙하신 분".

목표는 패턴 이름 암기가 아니라 **"왜 이렇게 설계했는가"를 설명할 수 있는 것** — 1차 인터뷰가 설계 면접이라 여기서 바로 평가받는다.

## 1. OOP 4대 원칙 — Java 코드로 감 잡기

### 캡슐화 (Encapsulation)

```java
// 나쁜 예: 필드 직접 노출, 어디서든 잘못된 값을 넣을 수 있음
public class Account {
    public int balance;
}
account.balance = -1000; // 막을 방법이 없음

// 좋은 예: 상태 변경을 메서드로만 허용, 불변식(invariant)을 클래스가 지킴
public class Account {
    private int balance;

    public void withdraw(int amount) {
        if (amount > balance) throw new IllegalStateException("잔액 부족");
        balance -= amount;
    }
}
```
- 캡슐화 = "이 객체의 상태는 이 객체만 책임진다". 필드를 private으로 감추는 게 목적이 아니라, **불변식을 깨는 경로를 원천 차단**하는 게 목적.

### 상속 (Inheritance) vs 조합(Composition)

```java
// 상속: "is-a" 관계가 아니면 오용. 흔한 실수: 코드 재사용 목적으로만 상속
class Bird { void fly() {} }
class Penguin extends Bird {} // 펭귄은 못 나는데 fly()가 강제로 딸려옴 — LSP 위반 (아래 참고)

// 조합: "has-a" 관계로 필요한 동작만 위임
class Engine { void start() {} }
class Car {
    private final Engine engine = new Engine();
    void start() { engine.start(); }
}
```
- 실무 기준: **"조합 우선, 상속은 정말 is-a일 때만"**. Spring Bean 설계도 대부분 조합(의존성 주입) 기반.

### 다형성 (Polymorphism)

```java
interface PaymentMethod {
    void pay(int amount);
}
class CardPayment implements PaymentMethod {
    public void pay(int amount) { /* 카드 결제 로직 */ }
}
class PointPayment implements PaymentMethod {
    public void pay(int amount) { /* 포인트 결제 로직 */ }
}

void checkout(PaymentMethod method, int amount) {
    method.pay(amount); // 호출부는 구체 타입을 모름 — 이게 핵심
}
```
- 다형성의 실전 가치: **호출부가 구현을 몰라도 되게 만드는 것**. 이게 바로 아래 4번 Strategy 패턴이고, Spring DI의 근간이다.

### 추상화 (Abstraction)

- "무엇을 하는지"만 노출하고 "어떻게 하는지"는 감춤. `interface PaymentMethod`가 추상화, `CardPayment`가 구현.
- 인터뷰에서 자주 나오는 질문: "인터페이스와 추상클래스 차이는?" → 인터페이스는 다중 구현 가능(계약), 추상클래스는 공통 구현 일부를 공유하면서 나머지를 강제(템플릿). Java 8+ 이후 인터페이스도 default 메서드를 가질 수 있어 경계가 흐려졌다는 점까지 언급하면 좋음.

## 2. SOLID — 각 원칙을 "왜 지키는가" 중심으로

| 원칙 | 한 줄 정의 | 위반하면 생기는 문제 |
|---|---|---|
| **S**ingle Responsibility | 클래스는 변경 이유가 하나여야 함 | 한 기능 수정이 관련 없는 기능을 깨뜨림 |
| **O**pen-Closed | 확장엔 열려있고 변경엔 닫혀있어야 함 | 새 케이스 추가할 때마다 기존 코드에 if/else 추가 |
| **L**iskov Substitution | 자식 타입은 부모 타입을 완전히 대체 가능해야 함 | 위 Penguin 예시 — 다형성이 거짓말이 됨 |
| **I**nterface Segregation | 인터페이스는 클라이언트가 쓰지 않는 메서드를 강제하면 안 됨 | 거대 인터페이스 하나 때문에 안 쓰는 메서드까지 구현 |
| **D**ependency Inversion | 고수준 모듈이 저수준 모듈의 구체 클래스에 의존하면 안 됨 | 구현을 바꾸면 상위 로직까지 다 고쳐야 함 |

**DIP(의존성 역전)가 제일 중요** — Spring이 존재하는 이유 그 자체다.

```java
// DIP 위반: OrderService가 구체 클래스(MysqlOrderRepository)에 직접 의존
class OrderService {
    private final MysqlOrderRepository repo = new MysqlOrderRepository();
}

// DIP 준수: OrderService는 인터페이스에만 의존, 구현체는 외부에서 주입
class OrderService {
    private final OrderRepository repo; // interface
    OrderService(OrderRepository repo) { this.repo = repo; } // 생성자 주입
}
```
- Spring의 `@Autowired`/생성자 주입은 "누가 구현체를 만들어서 넣어주는가"를 프레임워크가 대신해주는 것뿐 — **DI 컨테이너는 DIP를 편하게 실천하는 도구**라고 설명할 수 있어야 함.

## 3. 자주 쓰이는 디자인패턴 (Spring/백엔드 맥락)

### Strategy — 알고리즘을 교체 가능하게

위 `PaymentMethod` 예시가 정확히 Strategy 패턴. 결제수단, 할인정책, 정렬기준처럼 **"같은 문제를 여러 방식으로 풀 수 있고, 런타임에 방식을 고를 때"** 사용.

### Factory — 객체 생성 책임을 분리

```java
interface NotificationSender { void send(String msg); }

class NotificationFactory {
    static NotificationSender create(String type) {
        return switch (type) {
            case "EMAIL" -> new EmailSender();
            case "SMS" -> new SmsSender();
            default -> throw new IllegalArgumentException("unknown type: " + type);
        };
    }
}
```
- "new를 어디서 하느냐"의 문제. Spring에서는 `@Bean`/`@Component` + DI 컨테이너가 사실상 팩토리 역할을 대신한다 — **"Spring 컨테이너 자체가 거대한 Factory + Singleton 관리자"**라는 관점이 인터뷰에서 좋은 답변이 됨.

### Singleton — 인스턴스를 하나만 유지

```java
public class ConfigLoader {
    private static final ConfigLoader INSTANCE = new ConfigLoader();
    private ConfigLoader() {}
    public static ConfigLoader getInstance() { return INSTANCE; }
}
```
- Spring Bean은 **기본 스코프가 singleton**. 직접 이 패턴을 손으로 짤 일은 거의 없지만, "Spring Bean이 왜 기본적으로 싱글톤인지, 그게 왜 stateless해야 안전한지"는 반드시 설명 가능해야 함 (동시성 이슈로 07번 문서와 연결).

### Decorator — 기능을 감싸서 확장

```java
interface DataSource { String read(); }
class FileDataSource implements DataSource {
    public String read() { return "raw data"; }
}
class EncryptedDataSource implements DataSource { // 원본을 감싸서 기능 추가
    private final DataSource wrapped;
    EncryptedDataSource(DataSource wrapped) { this.wrapped = wrapped; }
    public String read() { return decrypt(wrapped.read()); }
    private String decrypt(String s) { return s; /* 예시 */ }
}
```
- Spring AOP(`@Transactional`, `@Cacheable` 등)가 내부적으로 프록시 기반 Decorator 패턴이다 — "왜 `@Transactional`이 self-invocation(같은 클래스 내부 메서드 호출)에서는 안 먹는지"까지 설명할 수 있으면 실전 이해도가 드러남 (프록시가 메서드 호출을 가로채는 방식이라 내부 호출은 프록시를 거치지 않기 때문).

### Template Method — 알고리즘 골격은 고정, 세부만 하위클래스가 채움

```java
abstract class ReportGenerator {
    final String generate() { // 골격 고정 (final)
        String data = fetchData();
        return format(data);
    }
    abstract String fetchData();
    abstract String format(String data);
}
```
- Spring의 `JdbcTemplate`, `RestTemplate`이 이름 그대로 이 패턴 — 반복되는 정형 로직(연결/예외처리/자원 해제)은 템플릿이 처리하고, 실제 쿼리/요청만 콜백으로 받는다.

### Observer — 상태 변화를 구독자에게 통지

- Spring의 `ApplicationEventPublisher` + `@EventListener`가 Observer 패턴. 도메인 이벤트(예: "주문 생성됨")를 발행하고 여러 구독자가 각자 반응하게 할 때 사용 — MSA 환경에서 카프카/이벤트 기반 통신 개념(06번 문서)으로 자연스럽게 확장됨.

## 4. 인터뷰에서 실제로 나오는 질문 형태

- "이 코드에 if-else가 5개 있는데 6번째 케이스가 추가되면?" → OCP 위반 지적 + Strategy/다형성으로 리팩토링 제안
- "왜 인터페이스를 두고 구현체를 주입받게 짰나요?" → DIP + 테스트 용이성(Mock 교체 가능) + 결합도 감소
- "Spring Bean 기본 스코프가 singleton인데 인스턴스 필드에 요청별 상태를 저장하면?" → 동시성 버그, stateless 설계 원칙과 연결
- "상속 대신 조합을 쓰라는 게 무슨 뜻인가요?" → LSP 위반 사례(Penguin) + 결합도

## 실습 방법

1. 위 코드 스니펫을 실제로 IDE에 쳐보면서 컴파일/실행 확인 (눈으로만 읽지 말 것 — 손으로 짜는 감각이 부족한 게 자가진단 원인이므로 여기서 메꿔야 함)
2. `project/` 착수 후: `PaymentMethod` 류 Strategy를 실제 도메인(예: document 저장 방식, 검색 랭킹 방식)에 적용해보기
3. 자신의 기존 코드(다른 프로젝트 등)에서 SOLID 위반 사례를 하나 찾아 리팩토링해보기 — "내가 이미 잘한 것"과 "고쳐야 할 것"을 둘 다 말할 수 있으면 인터뷰에서 강함

## 참고 키워드 (검색용)
- SOLID principles Java
- Liskov Substitution Principle violation example
- Spring Bean scope singleton stateless
- Spring AOP proxy self-invocation problem
- Gang of Four design patterns
