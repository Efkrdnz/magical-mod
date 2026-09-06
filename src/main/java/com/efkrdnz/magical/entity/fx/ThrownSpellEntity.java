package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.magic.service.InterceptableSpell;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A gravity projectile with drag, bounces and a fuse. The behaviour receives {@link #onLand} via
 * its tick (phase flips to PHASE_ACTIVE with the landing normal in serverData) so lobbed skills can
 * turn into whatever they become on arrival.
 */
public class ThrownSpellEntity extends SpellEffectEntity implements InterceptableSpell {
    private float gravity = 0.05F;
    private float drag = 0.99F;
    private int bouncesLeft;
    private float bounceScale = 0.4F;
    private int fuse;
    private boolean landed;

    public ThrownSpellEntity(EntityType<? extends ThrownSpellEntity> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public static ThrownSpellEntity create(ServerLevel level, SpellEffectEntity template, Vec3 pos, Vec3 velocity, float gravity, float drag, int bounces, int fuse) {
        ThrownSpellEntity entity = new ThrownSpellEntity(MagicalEntities.THROWN_SPELL.get(), level);
        CompoundTag tag = new CompoundTag();
        template.addAdditionalSaveData(tag);
        tag.remove("Synced");
        tag.remove("Data");
        entity.readAdditionalSaveData(tag);
        entity.setLife(template.life());
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(velocity);
        entity.setDirection(velocity.normalize());
        entity.gravity = gravity;
        entity.drag = drag;
        entity.bouncesLeft = bounces;
        entity.fuse = fuse;
        return entity;
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel)) {
            super.tick();
            return;
        }
        // integrate before the behaviour tick so landing is visible to it
        if (!landed) {
            Vec3 v = getDeltaMovement();
            Vec3 from = position();
            Vec3 to = from.add(v);
            BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                if (bouncesLeft > 0) {
                    bouncesLeft--;
                    Vec3 r = v.subtract(n.scale(2.0D * v.dot(n))).scale(bounceScale);
                    setPos(hit.getLocation().add(n.scale(0.05D)));
                    setDeltaMovement(r);
                } else {
                    setPos(hit.getLocation().add(n.scale(0.02D)));
                    land(n);
                }
            } else {
                setPos(to);
                v = v.add(0.0D, -gravity, 0.0D).scale(drag);
                setDeltaMovement(v);
                if (v.lengthSqr() > 1.0E-6D) {
                    setDirection(v.normalize());
                }
            }
            if (fuse > 0 && tickCount >= fuse && !landed) {
                land(new Vec3(0.0D, 1.0D, 0.0D));
            }
        }
        super.tick();
    }

    private void land(Vec3 normal) {
        landed = true;
        setDeltaMovement(Vec3.ZERO);
        serverData().putDouble("NormalX", normal.x);
        serverData().putDouble("NormalY", normal.y);
        serverData().putDouble("NormalZ", normal.z);
        setPhase(PHASE_ACTIVE);
    }

    public boolean landed() {
        return landed;
    }

    public Vec3 landingNormal() {
        CompoundTag d = serverData();
        return new Vec3(d.getDouble("NormalX"), d.getDouble("NormalY"), d.getDouble("NormalZ"));
    }

    @Override
    public Entity spellOwner() {
        return owner();
    }

    @Override
    public boolean isNoGravity() {
        return true; // we integrate our own gravity
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        gravity = tag.contains("Gravity") ? tag.getFloat("Gravity") : gravity;
        drag = tag.contains("Drag") ? tag.getFloat("Drag") : drag;
        bouncesLeft = tag.getInt("Bounces");
        fuse = tag.getInt("Fuse");
        landed = tag.getBoolean("Landed");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Gravity", gravity);
        tag.putFloat("Drag", drag);
        tag.putInt("Bounces", bouncesLeft);
        tag.putInt("Fuse", fuse);
        tag.putBoolean("Landed", landed);
    }
}
