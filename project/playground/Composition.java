public class Composition {
    // 상속: "is-a" 관계가 아니면 오용. 흔한 실수: 코드 재사용 목적으로만 상속
    class Bird {
        void fly() {
        }
    }

    // 펭귄은 못 나는데 fly()가 강제로 딸려옴 — LSP 위반 (아래 참고)
    class Penguin extends Bird {
    }

    // 조합: "has-a" 관계로 필요한 동작만 위임
    class Engine {
        void start() {
        }
    }

    class Car {
        private final Engine engine = new Engine();

        void start() {
            engine.start();
        }
    }

}
