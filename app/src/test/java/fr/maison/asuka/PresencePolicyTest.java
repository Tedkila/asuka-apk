package fr.maison.asuka;
import org.junit.Test;
import static org.junit.Assert.*;
public class PresencePolicyTest {
    @Test public void sleepBoundariesMatchHouseSchedule() {
        int start=PresencePolicy.time("01:30"), end=PresencePolicy.time("09:00");
        assertFalse(PresencePolicy.sleeping(89,start,end));
        assertTrue(PresencePolicy.sleeping(90,start,end));
        assertTrue(PresencePolicy.sleeping(539,start,end));
        assertFalse(PresencePolicy.sleeping(540,start,end));
    }
    @Test public void sleepAcrossMidnightAndDisabledSchedule() {
        assertTrue(PresencePolicy.sleeping(30,1380,420));
        assertTrue(PresencePolicy.sleeping(1400,1380,420));
        assertFalse(PresencePolicy.sleeping(800,1380,420));
        assertFalse(PresencePolicy.sleeping(100,90,90));
    }
    @Test public void cosineIsScaleIndependentAndRejectsInvalidSignatures() {
        assertEquals(1,PresencePolicy.cosine(new float[]{1,2},new float[]{2,4}),1e-6);
        assertEquals(0,PresencePolicy.cosine(new float[]{1,0},new float[]{0,1}),1e-6);
        assertEquals(-1,PresencePolicy.cosine(new float[]{0,0},new float[]{1,0}),1e-6);
        assertEquals(-1,PresencePolicy.cosine(new float[]{1},new float[]{1,0}),1e-6);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidTimeRejected() { PresencePolicy.time("25:90"); }
}
