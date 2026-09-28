package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import com.efkrdnz.magical.magic.visual.sigil.SigilMotion;
import com.efkrdnz.magical.particle.SigilParticleOptions;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

class SigilParticleOptionsTest {
    private static final ParticleType<SigilParticleOptions> TYPE = new ParticleType<>(false) {
        @Override
        public MapCodec<SigilParticleOptions> codec() {
            return SigilParticleOptions.codec(this);
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, SigilParticleOptions> streamCodec() {
            return SigilParticleOptions.streamCodec(this);
        }
    };

    @Test
    void everySigilAndMotionCrossesTheWire() {
        StreamCodec<RegistryFriendlyByteBuf, SigilParticleOptions> codec = SigilParticleOptions.streamCodec(TYPE);
        int i = 0;
        for (Sigil sigil : Sigil.values()) {
            SigilMotion motion = SigilMotion.values()[i++ % SigilMotion.values().length];
            SigilParticleOptions sent = new SigilParticleOptions(TYPE, sigil, 0x123456 + i, 0xFEDCBA - i, 1.25F, motion);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            codec.encode(buffer, sent);
            assertEquals(sent, codec.decode(buffer));
        }
    }

    @Test
    void theMapCodecSpellsTheSymbolAndMotionByName() {
        Codec<SigilParticleOptions> codec = SigilParticleOptions.codec(TYPE).codec();
        SigilParticleOptions options = new SigilParticleOptions(TYPE, Sigil.HOURGLASS, 0xFFF6D0, 0xB86E00, 1.5F, SigilMotion.HOVER);
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, options).getOrThrow();
        assertEquals("hourglass", json.getAsJsonObject().get("sigil").getAsString());
        assertEquals("hover", json.getAsJsonObject().get("motion").getAsString());
        assertEquals(options, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void aSigilIsClampedLikeEveryTintedSprite() {
        SigilParticleOptions huge = new SigilParticleOptions(TYPE, Sigil.EYE, 0xFF123456, 0xFF654321, 99.0F, SigilMotion.RISE);
        assertEquals(TintedParticleOptions.MAX_SCALE, huge.scale());
        assertEquals(0x123456, huge.core());
        assertEquals(0x654321, huge.glow());
    }
}
