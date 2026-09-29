package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InsistTest {
    @Test
    void insistingHelpsAMindHalfwayThereAndHardensOneThatDoubts() {
        assertEquals(0.01F, Insist.push(0.3F), 1.0E-6F);
        assertEquals(0.01F, Insist.push(0.9F), 1.0E-6F);
        assertEquals(-0.01F, Insist.push(0.29F), 1.0E-6F);
        assertEquals(-0.01F, Insist.push(0.0F), 1.0E-6F);
    }
}
