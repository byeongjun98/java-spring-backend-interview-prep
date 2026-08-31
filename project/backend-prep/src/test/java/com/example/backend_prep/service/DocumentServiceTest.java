package com.example.backend_prep.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.example.backend_prep.domain.Document;
import com.example.backend_prep.repository.DocumentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// @SpringBootTest: 실제 스프링 컨테이너(빈, DB 연결 포함)를 통째로 띄워서 테스트함.
// 여기선 진짜 PostgreSQL(로컬)에 실제로 접속해서 검증함 — 이 프로젝트엔 H2 같은
// 인메모리 DB를 안 쓰기 때문에, 이 테스트가 남긴 데이터(테스트용 문서 1건)는
// 실제로 로컬 DB에 남는다는 점 참고.
@SpringBootTest
class DocumentServiceTest {

    @Autowired
    private DocumentRepository documentRepository;

    // EntityManager: Hibernate가 "지금 이 트랜잭션(영속성 컨텍스트) 안에서 어떤 엔티티를
    // 관리 중인지" 기억하는 1차 캐시 같은 것. @Autowired 대신 @PersistenceContext를 쓰는 게
    // JPA 표준 방식(Spring 빈이 아니라 JPA 스펙에서 온 관리 대상이라서 어노테이션이 다름).
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 낙관적_락_충돌_재현() {
        // 테스트 전용 문서 하나 새로 만듦 (기존 더미 데이터 건드리지 않기 위해).
        // saveAndFlush: save()는 영속성 컨텍스트에 반영만 하고 실제 SQL은 나중에(트랜잭션
        // 끝나거나 필요할 때) 나갈 수 있는데, flush까지 강제해서 지금 즉시 DB에 INSERT 나가게 함.
        Document saved = documentRepository.saveAndFlush(new Document("낙관적 락 테스트", "원본 내용", null));
        Integer id = saved.getId();

        // "사용자 A가 문서를 읽음" — 이 시점 DB의 version은 0. userA는 그 상태를 들고 있음.
        Document userA = documentRepository.findById(id).orElseThrow();

        // entityManager.clear(): 지금까지 이 영속성 컨텍스트가 캐시해둔 엔티티를 전부 비움.
        // 이걸 안 하면 바로 아래 findById(id)가 "이미 관리 중인 같은 id 엔티티"를 캐시에서
        // 그대로 재사용해버려서(1차 캐시 히트) userA와 userB가 사실상 같은 객체가 되어버림 —
        // 그러면 "서로 다른 사용자가 각자 읽었다"는 상황을 재현할 수 없음. clear()로 캐시를
        // 비워야 아래 조회가 진짜로 DB까지 다시 가서 새 인스턴스로 읽어옴.
        entityManager.clear();

        // "사용자 B도 같은 문서를 읽음" — DB는 아직 그대로라 B가 읽은 version도 0.
        // (userA와는 완전히 다른 자바 객체지만, DB 값은 같은 시점의 것)
        Document userB = documentRepository.findById(id).orElseThrow();

        // A가 먼저 수정하고 저장 → 성공. DB의 version이 0 -> 1로 올라감.
        userA.changeContent("A가 수정한 내용");
        documentRepository.saveAndFlush(userA);

        // B는 여전히 "version=0일 때의 문서"를 들고 있는 채로 수정 시도.
        // Hibernate가 저장 시 "UPDATE document SET ..., version=1 WHERE id=? AND
        // version=0"을
        // 날리는데, DB엔 이미 version=1이라 조건에 안 걸려서 0건 반영됨 -> Hibernate가 이걸
        // "누군가 먼저 바꿔서 내가 든 버전이 낡았다"고 판단해 예외를 던짐.
        //
        // assertThrows(예외타입.class, () -> ...): 괄호 안 람다를 실행했을 때 지정한 타입의
        // 예외가 실제로 던져지는지 검증. 예외가 안 던져지거나 다른 타입이면 테스트 실패.
        // ObjectOptimisticLockingFailureException: Spring Data가 Hibernate의
        // OptimisticLockException을 감싸서 던지는 예외 (Repository 계층에서 나오는 건 이 타입).
        userB.changeContent("B가 수정한 내용");
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> documentRepository.saveAndFlush(userB));
    }

    @Test
    void 낙관적_락_동시성_재현() throws InterruptedException, ExecutionException {
        // 1. 테스트용 문서 준비
        Document saved = documentRepository.saveAndFlush(new Document("동시성 테스트", "원본 내용", null));
        Integer id = saved.getId();

        // 스레드 2개짜리 풀. 아래 invokeAll에서 task 2개를 각각 다른 스레드에 하나씩 배정해서 돌림.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 카운트 2에서 시작. 스레드가 "나 읽기 끝났다"고 countDown() 호출할 때마다 1씩 깎임.
        // 두 스레드 다 await()에서 멈춰서 서로를 기다리다가, 카운트가 0이 되는 순간(=둘 다
        // 읽기를 마친 순간) 동시에 풀려나서 다음 줄(수정+저장)로 넘어감 — 이게 "같은 버전을
        // 든 채로 동시에 쓰기 시도"하는 상황을 스레드 타이밍에 기대지 않고 강제하는 장치.
        CountDownLatch readLatch = new CountDownLatch(2);

        // 같은 로직인데 content만 다르게 실행해야 해서, 파라미터(id, 넣을 content, latch)를
        // 받아 Callable을 만들어주는 메서드로 뺐음. 람다 안에서 바깥 변수(id, readLatch)를
        // 그대로 참조("캡처")하는데, 자바 람다는 캡처하는 지역변수가 사실상 값이 안 바뀌는
        // 변수(effectively final)여야 해서, 메서드 파라미터로 넘기는 편이 깔끔함.
        Callable<Boolean> taskA = createUpdateTask(id, "A가 수정한 내용", readLatch);
        Callable<Boolean> taskB = createUpdateTask(id, "B가 수정한 내용", readLatch);

        // invokeAll: 넘긴 task들을 스레드 풀에 던지고 전부 끝날 때까지 기다린 다음,
        // 각 결과를 담은 Future 리스트를 돌려줌. Future.get()으로 실제 결과(또는 task 안에서
        // 처리 안 하고 새어나온 예외)를 꺼낼 수 있음.
        List<Future<Boolean>> futures = executor.invokeAll(List.of(taskA, taskB));
        executor.shutdown();

        // Future.get()은 checked exception(ExecutionException)을 던질 수 있어서 try-catch
        // 없이 쓰려면 메서드 시그니처에 throws로 위임(테스트 메서드 위에 추가해둠).
        long successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }

        // 6. 검증: 둘 중 정확히 하나만 성공(true)해야 함 — 나머지 하나는 changeContent 이후
        // saveAndFlush에서 ObjectOptimisticLockingFailureException을 만나 task 안에서
        // false로 처리됐어야 함.
        assertEquals(1, successCount);
    }

    // task 하나가 하는 일: 문서를 읽고 -> 상대 스레드도 읽을 때까지 기다렸다가 -> 동시에 수정 저장 시도.
    private Callable<Boolean> createUpdateTask(Integer id, String newContent, CountDownLatch readLatch) {
        return () -> {
            // "사용자가 문서를 읽음" — 이 Callable을 실행하는 스레드마다 자기 트랜잭션에서
            // 새로 조회하는 것이므로, 이전 테스트에서 썼던 entityManager.clear()가 필요 없음
            // (트랜잭션/영속성 컨텍스트가 스레드별로 이미 분리되어 있음).
            Document doc = documentRepository.findById(id).orElseThrow();

            readLatch.countDown();
            readLatch.await();

            try {
                doc.changeContent(newContent);
                documentRepository.saveAndFlush(doc);
                return true;
            } catch (ObjectOptimisticLockingFailureException e) {
                return false;
            }
        };
    }
}
