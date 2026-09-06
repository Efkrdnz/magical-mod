package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.arcane.ArcanePlayerData;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class MagicalAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MagicalMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ArcanePlayerData>> ARCANE_DATA = ATTACHMENTS.register(
            "arcane_data",
            () -> AttachmentType.builder(ArcanePlayerData::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, ArcanePlayerData>() {
                        @Override
                        public ArcanePlayerData read(IAttachmentHolder holder, CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
                            return ArcanePlayerData.load(tag);
                        }

                        @Override
                        public CompoundTag write(ArcanePlayerData attachment, net.minecraft.core.HolderLookup.Provider provider) {
                            return attachment.save();
                        }
                    })
                    .copyHandler((attachment, holder, provider) -> attachment.copy())
                    .copyOnDeath()
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerMagicState>> MAGIC_STATE = ATTACHMENTS.register(
            "magic_state",
            () -> AttachmentType.builder(PlayerMagicState::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, PlayerMagicState>() {
                        @Override
                        public PlayerMagicState read(IAttachmentHolder holder, CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
                            return PlayerMagicState.load(tag);
                        }

                        @Override
                        public CompoundTag write(PlayerMagicState attachment, net.minecraft.core.HolderLookup.Provider provider) {
                            return attachment.save();
                        }
                    })
                    .copyHandler((attachment, holder, provider) -> attachment.copy())
                    .copyOnDeath()
                    .build());

    /** Skill-applied combat statuses on any living entity; transient (never serialized). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.efkrdnz.magical.magic.status.MagicStatusData>> MAGIC_STATUS = ATTACHMENTS.register(
            "magic_status",
            () -> AttachmentType.builder(com.efkrdnz.magical.magic.status.MagicStatusData::new).build());

    private MagicalAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
