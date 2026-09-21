package com.efkrdnz.magical.magic.causality;

/**
 * One thing the {@link Weaver} decided should happen, for the service to go and do.
 *
 * <p>The engine never touches the world, so this is the whole of what it can say. A resolution is a
 * list of these and a number - what the consequence that started it has become - and that pair is
 * the entire interface between the pure half of the Authority and the half that knows what a level
 * is.
 *
 * <p>{@code magnitude} means whatever the {@link Effect} says it means: points of harm for
 * {@link Effect#SPEND}, points of consequence banked for {@link Effect#STORE}, points moved for
 * {@link Effect#PASS}. {@code param} is the number off the pin, already clamped. {@code delay} is
 * zero unless a {@link Modifier#AFTER} or the second half of a {@link Modifier#TWICE} put it there.
 */
public record CausalAction(
        int nodeId,
        Effect effect,
        Scope scope,
        float magnitude,
        int param,
        int delay,
        boolean shared,
        boolean turned) {

    public static CausalAction of(CausalNode node, float magnitude, int delay) {
        return new CausalAction(node.id(), node.effect(), node.scope(), magnitude, node.param(), delay,
                node.wears(Modifier.SHARED), node.wears(Modifier.TURNED));
    }

    public CausalAction delayedBy(int ticks) {
        return new CausalAction(nodeId, effect, scope, magnitude, param, delay + ticks, shared, turned);
    }

}
