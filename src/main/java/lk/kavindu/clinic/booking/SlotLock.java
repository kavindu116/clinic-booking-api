package lk.kavindu.clinic.booking;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class SlotLock {
    @PersistenceContext
    private EntityManager em;

    public void acquire(long doctorId, Instant slotStart){
        em.createNativeQuery("SELECT pg_advisory_xact_lock(CAST(?1 AS INT), CAST(?2 AS INT))")
                .setParameter(1,(int)doctorId)
                .setParameter(2,slotKey(slotStart))
                .getSingleResult();
    }


    static int slotKey(Instant slotStart){
        return (int) slotStart.getEpochSecond();
    }
}
