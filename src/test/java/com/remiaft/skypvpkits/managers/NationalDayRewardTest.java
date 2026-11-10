package com.remiaft.skypvpkits.managers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NationalDayRewardTest {
    @Test void ownershipLadderUsesEachFamilyAndNeverUnrelatedKits() {
        for (String family : new String[]{"moon", "mining"}) {
            check(family, 0, 0, family, 3);
            check(family, 2, 4, family, 3); // Galaxy alone does not qualify for compensation.
            check(family, 3, 0, family, 4);
            check(family, 3, 4, family, 4);
            check(family, 4, 0, "galaxy", 3);
            check(family, 4, 2, "galaxy", 3);
            assertEquals(5, KitManager.nationalDayReward(family, 4, 4, 3).gemAmount);
            assertEquals(KitManager.NationalDayReward.Type.GEMS,
                    KitManager.nationalDayReward(family, 4, 4, 4).type);
        }
        check("earth", 3, 4, "earth", 4);
        check("earth", 4, 0, "galaxy", 3);
        assertEquals(5, KitManager.nationalDayReward("earth", 4, 4, 3).gemAmount);
        for (String family : new String[]{"sky", "sun", "mars", "galaxy", "zxgy", "ywxc"})
            assertNull(KitManager.nationalDayReward(family, 5, 5, 4));
        assertNull(KitManager.nationalDayReward(null, 0, 4, 0));
        assertNull(KitManager.nationalDayReward("earth", 0, 3, 0));
    }

    private void check(String family, int owned, int galaxy, String expectedFamily, int level) {
        KitManager.NationalDayReward reward = KitManager.nationalDayReward(family, owned, 4, galaxy);
        assertEquals(KitManager.NationalDayReward.Type.KIT, reward.type);
        assertEquals(expectedFamily, reward.kitType);
        assertEquals(level, reward.level);
    }
}
