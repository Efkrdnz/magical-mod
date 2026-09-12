package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class UnwakingTextureTest {
    @Test void theBossSkinIsAStandardSizedOpaquePureWhitePlayerTexture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/magical/textures/entity/unwaking_god.png")) {
            assertNotNull(stream);
            var skin = ImageIO.read(stream);
            assertEquals(64, skin.getWidth()); assertEquals(64, skin.getHeight());
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) assertEquals(0xFFFFFFFF, skin.getRGB(x, y));
        }
    }
}
